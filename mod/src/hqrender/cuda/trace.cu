// trace.cu: CUDA port of hqrender's Renderer.trace (the CPU ray caster). tools/render-gpu.
//
// One thread = one sub-sample. Same maths as the Java: an orthographic primary ray walks the voxel grid (Amanatides-Woo),
// hits the textured quads of each cell, alpha-tests cutout texels, blends translucent ones front to back; every blended hit
// gets the smooth-lighting AO and the sun shadow rays. The result is the same packed 10-10-10 sqrt-encoded colour that
// Renderer.resolve() filters on the CPU, plus the emissive coverage byte for the bloom.
//
// Everything is in "clip-local" float coordinates (world minus the clip's min corner) so float precision holds: the ray
// origin is computed in double and moved to the point where the ray enters the clip box.
//
// Compiled to PTX with NVRTC (a dev tool, see docs/tools/hq-render.md "GPU"); the PTX is committed (gpu/TracePtx.java),
// at run time only the driver is needed. Keep the struct layout of Params in step with GpuTrace.java.

typedef unsigned int uint;
typedef unsigned long long u64;

struct Params {
	double s0, t1, px;
	double rx, ry, rz, ux, uy, uz, ddx, ddy, ddz;
	int W, H;
	int minX, minY, minZ, sx, sy, sz;
	int cminX, cminY, cminZ, csx, csy, csz;
	int flags;       // 1 sky, 2 ao, 4 soft shadows, 8 hard shadows, 16 bloom
	int shadowRays;
	float sunSpread;
	float sunx, suny, sunz, sunUx, sunUy, sunUz, sunVx, sunVy, sunVz;
	float dfx, dfy, dfz;
};

struct Ctx {
	const Params* P;
	const int* cells;
	const int* mStart;
	const int* mQuads;
	const int* mMask;
	const float* quads;   // 32 words per quad
	const int* tex;
	const float* lin;     // sRGB byte -> linear
};

struct Hit {
	float t;
	int qi;
	uint argb;
	float px, py, pz;
};

#define MAXP 12
#define QW 32
#define QI(q, k) (((const int*)(q))[k])

__constant__ float SUN_C[3] = {1.02f, 0.95f, 0.82f};
__constant__ float SKY_C[3] = {0.27f, 0.31f, 0.37f};
__constant__ float GROUND_C[3] = {0.19f, 0.17f, 0.14f};
__constant__ float EMIT_C[3] = {1.30f, 1.26f, 1.18f};

// ------------------------------------------------------------------ grid access

__device__ __forceinline__ int modelAt(const Ctx& c, int i, int j, int k) {
	const Params* P = c.P;
	if (i < 0 || j < 0 || k < 0 || i >= P->csx || j >= P->csy || k >= P->csz) {
		return 0;
	}
	int x = P->cminX + i - P->minX;
	int y = P->cminY + j - P->minY;
	int z = P->cminZ + k - P->minZ;
	if (x < 0 || y < 0 || z < 0 || x >= P->sx || y >= P->sy || z >= P->sz) {
		return 0;
	}
	return c.cells[(y * P->sz + z) * P->sx + x];
}

// ------------------------------------------------------------------ hashing and small helpers

__device__ __forceinline__ float hash01(int a, int b) {
	u64 z = ((u64)(long long)a * 0x9E3779B97F4A7C15ULL) ^ ((u64)(long long)b * 0xC2B2AE3D27D4EB4FULL);
	z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9ULL;
	z = (z ^ (z >> 27)) * 0x94D049BB133111EBULL;
	z ^= z >> 31;
	return (float)(uint)(z >> 40) * (1.0f / 16777216.0f);
}

__device__ __forceinline__ int enc(float v) {
	v = fminf(1.0f, fmaxf(0.0f, v));
	return (int)floorf(sqrtf(v) * 1023.0f + 0.5f);
}

__device__ __forceinline__ float tone(float x) {
	return x <= 0.6f ? x : 0.6f + 0.4f * (1.0f - expf(-(x - 0.6f) / 0.4f));
}

// ------------------------------------------------------------------ voxel walk

struct Walker {
	int x, y, z;
	float tExit;
	int stepX, stepY, stepZ;
	float tMaxX, tMaxY, tMaxZ, tDX, tDY, tDZ, tEnd;
	bool started;

	__device__ __forceinline__ void init(float ox, float oy, float oz, float dx, float dy, float dz, float tStart, float tEnd_) {
		tEnd = tEnd_;
		float t = tStart;
		float px = ox + dx * t, py = oy + dy * t, pz = oz + dz * t;
		x = (int)floorf(px);
		y = (int)floorf(py);
		z = (int)floorf(pz);
		stepX = dx > 0.0f ? 1 : -1;
		stepY = dy > 0.0f ? 1 : -1;
		stepZ = dz > 0.0f ? 1 : -1;
		tDX = fabsf(dx) < 1e-12f ? 1e30f : fabsf(1.0f / dx);
		tDY = fabsf(dy) < 1e-12f ? 1e30f : fabsf(1.0f / dy);
		tDZ = fabsf(dz) < 1e-12f ? 1e30f : fabsf(1.0f / dz);
		tMaxX = fabsf(dx) < 1e-12f ? 1e30f : t + (dx > 0.0f ? (x + 1 - px) : (px - x)) * tDX;
		tMaxY = fabsf(dy) < 1e-12f ? 1e30f : t + (dy > 0.0f ? (y + 1 - py) : (py - y)) * tDY;
		tMaxZ = fabsf(dz) < 1e-12f ? 1e30f : t + (dz > 0.0f ? (z + 1 - pz) : (pz - z)) * tDZ;
		tExit = 0.0f;
		started = false;
	}

	__device__ __forceinline__ bool next() {
		if (started) {
			if (tMaxX < tMaxY && tMaxX < tMaxZ) {
				x += stepX;
				tMaxX += tDX;
			} else if (tMaxY < tMaxZ) {
				y += stepY;
				tMaxY += tDY;
			} else {
				z += stepZ;
				tMaxZ += tDZ;
			}
		}
		started = true;
		float enter = tExit;
		tExit = fminf(tMaxX, fminf(tMaxY, tMaxZ));
		return enter <= tEnd;
	}
};

// ------------------------------------------------------------------ quads

__device__ __forceinline__ uint sampleQuad(const Ctx& c, const float* q, float a, float b) {
	int uvRot = QI(q, 18);
	float ra = a, rb = b;
	if (uvRot == 90) {
		ra = b;
		rb = 1.0f - a;
	} else if (uvRot == 180) {
		ra = 1.0f - a;
		rb = 1.0f - b;
	} else if (uvRot == 270) {
		ra = 1.0f - b;
		rb = a;
	}
	int w = QI(q, 22), h = QI(q, 23);
	float u = (q[14] + ra * q[16]) * (1.0f / 16.0f);
	float v = (q[15] + rb * q[17]) * (1.0f / 16.0f);
	int xi = (int)floorf(u * w);
	int yi = (int)floorf(v * h);
	xi = max(0, min(w - 1, xi));
	yi = max(0, min(h - 1, yi));
	uint px = (uint)c.tex[QI(q, 21) + yi * w + xi];
	int tint = QI(q, 19);
	if (tint != 0xFFFFFF) {
		uint r = ((px >> 16) & 255) * ((uint)(tint >> 16) & 255) / 255;
		uint g = ((px >> 8) & 255) * ((uint)(tint >> 8) & 255) / 255;
		uint bl = (px & 255) * ((uint)tint & 255) / 255;
		px = (px & 0xFF000000u) | (r << 16) | (g << 8) | bl;
	}
	return px;
}

// Ray/quad intersection in cell (cx,cy,cz); false on a miss, a back face (when culling) or a transparent texel.
__device__ __forceinline__ bool hitQuad(const Ctx& c, int qi, int cx, int cy, int cz, float ox, float oy, float oz, float dx, float dy, float dz,
		bool cull, float tmin, Hit& out) {
	const float* q = c.quads + (u64)qi * QW;
	float denom = q[9] * dx + q[10] * dy + q[11] * dz;
	if (cull ? denom >= -1e-9f : fabsf(denom) < 1e-9f) {
		return false;
	}
	float px = (float)cx + q[0], py = (float)cy + q[1], pz = (float)cz + q[2];
	float t = (q[9] * (px - ox) + q[10] * (py - oy) + q[11] * (pz - oz)) / denom;
	if (t < tmin) {
		return false;
	}
	float hx = ox + t * dx - px, hy = oy + t * dy - py, hz = oz + t * dz - pz;
	float a = (hx * q[3] + hy * q[4] + hz * q[5]) / q[12];
	float b = (hx * q[6] + hy * q[7] + hz * q[8]) / q[13];
	float ea = 1.5e-4f * q[24];
	float eb = 1.5e-4f * q[25];
	if (!(a >= -ea && a <= 1.0f + ea && b >= -eb && b <= 1.0f + eb)) {
		return false;
	}
	uint col = sampleQuad(c, q, fminf(a, 0.99999f), fminf(b, 0.99999f));
	if ((col >> 24) < 16) {
		return false;
	}
	out.t = t;
	out.qi = qi;
	out.argb = col;
	out.px = hx + px;
	out.py = hy + py;
	out.pz = hz + pz;
	return true;
}

// Entry/exit of a ray through the clip box; false when it misses.
__device__ __forceinline__ bool slabF(const Params* P, float ox, float oy, float oz, float dx, float dy, float dz, float& r0, float& r1) {
	float o[3] = {ox, oy, oz};
	float d[3] = {dx, dy, dz};
	float hi[3] = {(float)P->csx, (float)P->csy, (float)P->csz};
	float t0 = -3.0e38f, t1 = 3.0e38f;
#pragma unroll
	for (int i = 0; i < 3; i++) {
		if (fabsf(d[i]) < 1e-12f) {
			if (o[i] < 0.0f || o[i] > hi[i]) {
				return false;
			}
			continue;
		}
		float a = (0.0f - o[i]) / d[i];
		float b = (hi[i] - o[i]) / d[i];
		t0 = fmaxf(t0, fminf(a, b));
		t1 = fminf(t1, fmaxf(a, b));
	}
	if (!(t0 <= t1 && t1 >= 0.0f)) {
		return false;
	}
	r0 = fmaxf(t0, 0.0f);
	r1 = t1;
	return true;
}

// ------------------------------------------------------------------ shadows

__device__ bool occluded(const Ctx& c, float px, float py, float pz, float nx, float ny, float nz, float dx, float dy, float dz) {
	float ox = px + nx * 0.003f + dx * 0.003f;
	float oy = py + ny * 0.003f + dy * 0.003f;
	float oz = pz + nz * 0.003f + dz * 0.003f;
	float r0, r1;
	if (!slabF(c.P, ox, oy, oz, dx, dy, dz, r0, r1)) {
		return false;
	}
	Walker wk;
	wk.init(ox, oy, oz, dx, dy, dz, fmaxf(0.0f, r0), fminf(r1, 64.0f));
	while (wk.next()) {
		int m = modelAt(c, wk.x, wk.y, wk.z);
		if (m == 0) {
			continue;
		}
		int k1 = c.mStart[m + 1];
		for (int k = c.mStart[m]; k < k1; k++) {
			int qi = c.mQuads[k];
			int fl = QI(c.quads + (u64)qi * QW, 20);
			if ((fl & 1) && (fl & 4)) {
				continue; // emissive solid colour: lamps do not cast shadows
			}
			Hit h;
			if (hitQuad(c, qi, wk.x, wk.y, wk.z, ox, oy, oz, dx, dy, dz, false, 0.0f, h) && h.t > 1e-4f && (h.argb >> 24) > 200) {
				return true;
			}
		}
	}
	return false;
}

// Fraction of the sun that reaches p: soft (jittered rays over the sun disc) or one hard ray.
__device__ float sunVisibility(const Ctx& c, float px, float py, float pz, float nx, float ny, float nz, int sample) {
	const Params* P = c.P;
	if (P->flags & 4) {
		int rays = max(1, P->shadowRays);
		float lit = 0.0f;
		for (int i = 0; i < rays; i++) {
			float r1 = ((float)i + hash01(sample, 2 * i)) / (float)rays;
			float r2 = hash01(sample, 2 * i + 1);
			float rad = sqrtf(r2) * P->sunSpread;
			float ang = 6.2831853071795864f * r1;
			float ju = cosf(ang) * rad, jv = sinf(ang) * rad;
			float dx = P->sunx + P->sunUx * ju + P->sunVx * jv;
			float dy = P->suny + P->sunUy * ju + P->sunVy * jv;
			float dz = P->sunz + P->sunUz * ju + P->sunVz * jv;
			float inv = rsqrtf(dx * dx + dy * dy + dz * dz);
			if (!occluded(c, px, py, pz, nx, ny, nz, dx * inv, dy * inv, dz * inv)) {
				lit += 1.0f;
			}
		}
		return lit / (float)rays;
	}
	if (P->flags & 8) {
		return occluded(c, px, py, pz, nx, ny, nz, P->sunx, P->suny, P->sunz) ? 0.0f : 1.0f;
	}
	return 1.0f;
}

// ------------------------------------------------------------------ ambient occlusion

// Opaque half-block sub-cell at half-grid coordinates.
__device__ __forceinline__ int halfCell(const Ctx& c, int gx, int gy, int gz) {
	int m = c.mMask[modelAt(c, gx >> 1, gy >> 1, gz >> 1)];
	return m == 0 ? 0 : (m >> ((gx & 1) | ((gz & 1) << 1) | ((gy & 1) << 2))) & 1;
}

__device__ __forceinline__ float fullCell(const Ctx& c, int x, int y, int z) {
	return (float)__popc((uint)c.mMask[modelAt(c, x, y, z)]) * 0.125f;
}

__device__ float aoAt(const Ctx& c, float px, float py, float pz, float nx, float ny, float nz) {
	float p[3] = {px, py, pz};
	float n[3] = {nx, ny, nz};
	int a = fabsf(n[0]) >= fabsf(n[1]) ? (fabsf(n[0]) >= fabsf(n[2]) ? 0 : 2) : (fabsf(n[1]) >= fabsf(n[2]) ? 1 : 2);
	int u = (a + 1) % 3;
	int v = (a + 2) % 3;
	float sgn = n[a] >= 0.0f ? 1.0f : -1.0f;
	int cc[3];
	// half-block resolution
	float fu = p[u] * 2.0f, fv = p[v] * 2.0f;
	int iu = (int)floorf(fu), iv = (int)floorf(fv);
	float tu = fu - (float)iu, tv = fv - (float)iv;
	cc[a] = (int)floorf((p[a] + sgn * 0.25f) * 2.0f);
	float fine = 0.0f;
	for (int cu = 0; cu < 2; cu++) {
		for (int cv = 0; cv < 2; cv++) {
			int du = cu == 0 ? -1 : 1, dv = cv == 0 ? -1 : 1;
			cc[u] = iu + du;
			cc[v] = iv;
			float s1 = (float)halfCell(c, cc[0], cc[1], cc[2]);
			cc[u] = iu;
			cc[v] = iv + dv;
			float s2 = (float)halfCell(c, cc[0], cc[1], cc[2]);
			cc[u] = iu + du;
			cc[v] = iv + dv;
			float dg = (float)halfCell(c, cc[0], cc[1], cc[2]);
			float occ = s1 + s2 + fmaxf(dg, s1 * s2);
			fine += (cu == 0 ? 1.0f - tu : tu) * (cv == 0 ? 1.0f - tv : tv) * (1.0f - 0.14f * occ);
		}
	}
	// block resolution
	iu = (int)floorf(p[u]);
	iv = (int)floorf(p[v]);
	tu = p[u] - (float)iu;
	tv = p[v] - (float)iv;
	cc[a] = (int)floorf(p[a] + sgn * 0.5f);
	float broad = 0.0f;
	for (int cu = 0; cu < 2; cu++) {
		for (int cv = 0; cv < 2; cv++) {
			int du = cu == 0 ? -1 : 1, dv = cv == 0 ? -1 : 1;
			cc[u] = iu + du;
			cc[v] = iv;
			float s1 = fullCell(c, cc[0], cc[1], cc[2]);
			cc[u] = iu;
			cc[v] = iv + dv;
			float s2 = fullCell(c, cc[0], cc[1], cc[2]);
			cc[u] = iu + du;
			cc[v] = iv + dv;
			float dg = fullCell(c, cc[0], cc[1], cc[2]);
			float occ = s1 + s2 + fmaxf(dg, s1 * s2);
			broad += (cu == 0 ? 1.0f - tu : tu) * (cv == 0 ? 1.0f - tv : tv) * (1.0f - 0.11f * occ);
		}
	}
	return fine * broad;
}

// ------------------------------------------------------------------ lighting and blending

// Sky look: linear irradiance reaching the hit.
__device__ void irradiance(const Ctx& c, const Hit& h, int sample, float* e) {
	const Params* P = c.P;
	const float* q = c.quads + (u64)h.qi * QW;
	int fl = QI(q, 20);
	if (fl & 1) {
		e[0] = EMIT_C[0];
		e[1] = EMIT_C[1];
		e[2] = EMIT_C[2];
		return;
	}
	float nx = q[9], ny = q[10], nz = q[11];
	bool shade = (fl & 2) != 0;
	float ndl = shade ? nx * P->sunx + ny * P->suny + nz * P->sunz : 0.62f;
	float nyy = shade ? ny : 0.55f;
	float ao = (P->flags & 2) ? aoAt(c, h.px, h.py, h.pz, nx, ny, nz) : 1.0f;
	float vis = 0.0f;
	if (ndl > 0.0f) {
		vis = shade ? sunVisibility(c, h.px, h.py, h.pz, nx, ny, nz, sample) : sunVisibility(c, h.px, h.py, h.pz, P->sunx, P->suny, P->sunz, sample);
	}
	float direct = fmaxf(0.0f, ndl) * vis * (0.6f + 0.4f * ao);
	float skyW = 0.5f + 0.5f * nyy;
	for (int i = 0; i < 3; i++) {
		e[i] = SUN_C[i] * direct + (SKY_C[i] * skyW + GROUND_C[i] * (1.0f - skyW)) * ao;
	}
}

// The old flat look: Minecraft-like face factors plus a sun term (times AO when on).
__device__ float shadeFlat(const Ctx& c, const Hit& h, int sample) {
	const Params* P = c.P;
	const float* q = c.quads + (u64)h.qi * QW;
	int fl = QI(q, 20);
	if (fl & 1) {
		return 1.08f;
	}
	float nx = q[9], ny = q[10], nz = q[11];
	float face = fabsf(ny) > 0.5f ? (ny > 0.0f ? 1.0f : 0.55f) : (fabsf(nz) > fabsf(nx) ? 0.82f : 0.68f);
	float lambert = fmaxf(0.0f, nx * P->sunx + ny * P->suny + nz * P->sunz);
	float direct = 0.35f + 0.65f * lambert;
	if (lambert > 0.0f) {
		direct = 0.35f + (direct - 0.35f) * sunVisibility(c, h.px, h.py, h.pz, nx, ny, nz, sample);
	}
	float ao = (P->flags & 2) ? aoAt(c, h.px, h.py, h.pz, nx, ny, nz) : 1.0f;
	return face * (0.45f + 0.6f * direct) * ao;
}

// Front-to-back compositing of one hit into acc (r, g, b, alpha, emissive coverage).
__device__ void blend(const Ctx& c, float* acc, const Hit& h, float alpha, int sample) {
	float k = (1.0f - acc[3]) * alpha;
	uint col = h.argb;
	if (c.P->flags & 1) {
		float e[3];
		irradiance(c, h, sample, e);
		acc[0] += k * c.lin[(col >> 16) & 255] * e[0];
		acc[1] += k * c.lin[(col >> 8) & 255] * e[1];
		acc[2] += k * c.lin[col & 255] * e[2];
	} else {
		float light = shadeFlat(c, h, sample);
		acc[0] += k * (float)((col >> 16) & 255) * light;
		acc[1] += k * (float)((col >> 8) & 255) * light;
		acc[2] += k * (float)(col & 255) * light;
	}
	if (QI(c.quads + (u64)h.qi * QW, 20) & 1) {
		acc[4] += k;
	}
	acc[3] += k;
}

__device__ __forceinline__ void pushHit(Hit* pend, int& n, const Hit& h) {
	int i;
	if (n >= MAXP) {
		if (h.t >= pend[MAXP - 1].t) {
			return;
		}
		i = MAXP - 1;
	} else {
		i = n;
		n++;
	}
	while (i > 0 && pend[i - 1].t > h.t) {
		pend[i] = pend[i - 1];
		i--;
	}
	pend[i] = h;
}

__device__ __forceinline__ int lerp8(int c0, int c1, int shift, float f) {
	float a = (float)((c0 >> shift) & 255);
	float b = (float)((c1 >> shift) & 255);
	return (int)floorf(a * (1.0f - f) + b * f + 0.5f);
}

// ------------------------------------------------------------------ the kernel

extern "C" __global__ void trace(const Params* __restrict__ P, const int* __restrict__ cells, const int* __restrict__ mStart,
		const int* __restrict__ mQuads, const int* __restrict__ mMask, const float* __restrict__ quads, const int* __restrict__ tex,
		const float* __restrict__ lin, int* __restrict__ outBuf, unsigned char* __restrict__ outGlow, int rowStart, int rowEnd) {
	int x = blockIdx.x * blockDim.x + threadIdx.x;
	int py = rowStart + blockIdx.y * blockDim.y + threadIdx.y;
	if (x >= P->W || py >= rowEnd) {
		return;
	}
	Ctx c;
	c.P = P;
	c.cells = cells;
	c.mStart = mStart;
	c.mQuads = mQuads;
	c.mMask = mMask;
	c.quads = quads;
	c.tex = tex;
	c.lin = lin;
	int sample = py * P->W + x;
	float v = ((float)py + 0.5f) / (float)P->H;

	// ray origin (double) on the plane through the world origin, then the entry point into the clip box
	double s = P->s0 + ((double)x + 0.5) / P->px;
	double t = P->t1 - ((double)py + 0.5) / P->px;
	double o[3] = {s * P->rx + t * P->ux - (double)P->cminX, s * P->ry + t * P->uy - (double)P->cminY, s * P->rz + t * P->uz - (double)P->cminZ};
	double dd[3] = {P->ddx, P->ddy, P->ddz};
	double hi[3] = {(double)P->csx, (double)P->csy, (double)P->csz};
	double t0 = -1.0e300, t1 = 1.0e300;
	bool miss = false;
	for (int i = 0; i < 3; i++) {
		if (fabs(dd[i]) < 1e-12) {
			if (o[i] < 0.0 || o[i] > hi[i]) {
				miss = true;
			}
			continue;
		}
		double a = (0.0 - o[i]) / dd[i];
		double b = (hi[i] - o[i]) / dd[i];
		t0 = fmax(t0, fmin(a, b));
		t1 = fmin(t1, fmax(a, b));
	}
	if (!(t0 <= t1)) {
		miss = true;
	}

	float acc[5] = {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
	if (!miss) {
		float ox = (float)(o[0] + dd[0] * t0), oy = (float)(o[1] + dd[1] * t0), oz = (float)(o[2] + dd[2] * t0);
		float dx = P->dfx, dy = P->dfy, dz = P->dfz;
		Hit pend[MAXP];
		int np = 0;
		Walker wk;
		wk.init(ox, oy, oz, dx, dy, dz, 0.0f, (float)(t1 - t0));
		bool done = false;
		while (!done && wk.next()) {
			int m = modelAt(c, wk.x, wk.y, wk.z);
			if (m != 0) {
				int k1 = mStart[m + 1];
				for (int k = mStart[m]; k < k1; k++) {
					Hit h;
					if (hitQuad(c, mQuads[k], wk.x, wk.y, wk.z, ox, oy, oz, dx, dy, dz, true, -2e-4f, h)) {
						pushHit(pend, np, h);
					}
				}
			}
			while (np > 0 && pend[0].t <= wk.tExit + 1e-5f) {
				Hit h = pend[0];
				for (int i = 1; i < np; i++) {
					pend[i - 1] = pend[i];
				}
				np--;
				float alpha = (float)(h.argb >> 24) / 255.0f;
				if (alpha < 0.94f) {
					alpha *= 0.85f;
				}
				blend(c, acc, h, alpha, sample);
				if (acc[3] > 0.985f) {
					done = true;
					break;
				}
			}
		}
		if (!done) {
			for (int i = 0; i < np; i++) { // overhanging quads past the last cell
				blend(c, acc, pend[i], (float)(pend[i].argb >> 24) / 255.0f, sample);
			}
		}
	}

	float a = fminf(1.0f, acc[3]);
	float rest = 1.0f - a;
	float r, g, b;
	if (P->flags & 1) {
		float f = fminf(1.0f, fmaxf(0.0f, v));
		int bgR = lerp8(0xD3E2EF, 0xF4F2EE, 16, f);
		int bgG = lerp8(0xD3E2EF, 0xF4F2EE, 8, f);
		int bgB = lerp8(0xD3E2EF, 0xF4F2EE, 0, f);
		r = tone(acc[0]) + rest * lin[bgR];
		g = tone(acc[1]) + rest * lin[bgG];
		b = tone(acc[2]) + rest * lin[bgB];
	} else {
		// flat look: blended in sRGB like the old renderer, then taken to linear for the filter
		int ir = max(0, min(255, (int)floorf(acc[0] + rest * 238.0f + 0.5f)));
		int ig = max(0, min(255, (int)floorf(acc[1] + rest * 241.0f + 0.5f)));
		int ib = max(0, min(255, (int)floorf(acc[2] + rest * 244.0f + 0.5f)));
		r = lin[ir];
		g = lin[ig];
		b = lin[ib];
	}
	if ((P->flags & 16) && acc[4] > 0.0f) {
		outGlow[sample] = (unsigned char)(int)floorf(fminf(1.0f, acc[4]) * 255.0f + 0.5f);
	}
	outBuf[sample] = (enc(r) << 20) | (enc(g) << 10) | enc(b);
}

// ------------------------------------------------------------------ resolve (Renderer.resolve + bloom on the GPU)
//
// The sub-samples (packed 10-10-10 sqrt-encoded colours) become pixels: the separable filter taps wk[] over the sub-sample
// offsets kMin..kMax (the same for every pixel, computed on the host), resolved in linear light; then the bloom (emissive colour,
// gaussian blur, screen blend 15%); then the sRGB bytes. Every kernel takes a band of pixel rows [rowStart, rowEnd).

__device__ __forceinline__ float dec10(uint c, int shift) {
	float e = (float)((c >> shift) & 1023u) * (1.0f / 1023.0f);
	return e * e;
}

extern "C" __global__ void filterK(const int* __restrict__ buf, const float* __restrict__ wk, int W, int H, int w, int ss, int kMin, int kMax,
		float* __restrict__ lin, int rowStart, int rowEnd) {
	int x = blockIdx.x * blockDim.x + threadIdx.x;
	int y = rowStart + blockIdx.y * blockDim.y + threadIdx.y;
	if (x >= w || y >= rowEnd) {
		return;
	}
	float r = 0.0f, g = 0.0f, b = 0.0f, wsum = 0.0f;
	for (int ky = kMin; ky <= kMax; ky++) {
		int j = y * ss + ky;
		if (j < 0 || j >= H) {
			continue;
		}
		float wy = wk[ky - kMin];
		if (wy == 0.0f) {
			continue;
		}
		float rr = 0.0f, gg = 0.0f, bb = 0.0f, ws = 0.0f;
		for (int kx = kMin; kx <= kMax; kx++) {
			int i = x * ss + kx;
			if (i < 0 || i >= W) {
				continue;
			}
			float wx = wk[kx - kMin];
			uint c = (uint)buf[j * W + i];
			rr += wx * dec10(c, 20);
			gg += wx * dec10(c, 10);
			bb += wx * dec10(c, 0);
			ws += wx;
		}
		r += wy * rr;
		g += wy * gg;
		b += wy * bb;
		wsum += wy * ws;
	}
	float inv = 1.0f / (wsum == 0.0f ? 1.0f : wsum);
	int o = (y * w + x) * 3;
	lin[o] = fmaxf(0.0f, r * inv);
	lin[o + 1] = fmaxf(0.0f, g * inv);
	lin[o + 2] = fmaxf(0.0f, b * inv);
}

// emissive colour per pixel: the sub-samples' colour weighted by their emissive coverage byte
extern "C" __global__ void glowK(const int* __restrict__ buf, const unsigned char* __restrict__ glow, int W, int w, int ss, float* __restrict__ e,
		int rowStart, int rowEnd) {
	int x = blockIdx.x * blockDim.x + threadIdx.x;
	int y = rowStart + blockIdx.y * blockDim.y + threadIdx.y;
	if (x >= w || y >= rowEnd) {
		return;
	}
	float r = 0.0f, g = 0.0f, b = 0.0f;
	for (int j = 0; j < ss; j++) {
		for (int i = 0; i < ss; i++) {
			int k = (y * ss + j) * W + x * ss + i;
			int gl = glow[k];
			if (gl == 0) {
				continue;
			}
			uint c = (uint)buf[k];
			r += (float)gl * dec10(c, 20);
			g += (float)gl * dec10(c, 10);
			b += (float)gl * dec10(c, 0);
		}
	}
	float n = (float)(ss * ss) * 255.0f;
	int o = (y * w + x) * 3;
	e[o] = r / n;
	e[o + 1] = g / n;
	e[o + 2] = b / n;
}

extern "C" __global__ void blurH(const float* __restrict__ e, const float* __restrict__ k, int w, int rad, float* __restrict__ tmp, int rowStart, int rowEnd) {
	int x = blockIdx.x * blockDim.x + threadIdx.x;
	int y = rowStart + blockIdx.y * blockDim.y + threadIdx.y;
	if (x >= w || y >= rowEnd) {
		return;
	}
	float r = 0.0f, g = 0.0f, b = 0.0f;
	for (int i = -rad; i <= rad; i++) {
		int xx = max(0, min(w - 1, x + i));
		int o = (y * w + xx) * 3;
		float kk = k[i + rad];
		r += kk * e[o];
		g += kk * e[o + 1];
		b += kk * e[o + 2];
	}
	int o = (y * w + x) * 3;
	tmp[o] = r;
	tmp[o + 1] = g;
	tmp[o + 2] = b;
}

__device__ __forceinline__ uint srgbByte(float v) {
	v = fminf(1.0f, fmaxf(0.0f, v));
	float q = floorf(v * 4096.0f + 0.5f) * (1.0f / 4096.0f); // the CPU table has 4097 entries
	float c = q <= 0.0031308f ? q * 12.92f : 1.055f * powf(q, 1.0f / 2.4f) - 0.055f;
	return (uint)floorf(fminf(1.0f, fmaxf(0.0f, c)) * 255.0f + 0.5f);
}

// vertical blur of the bloom, screen blend onto lin, sRGB: one packed 0xRRGGBB per pixel
extern "C" __global__ void finalK(const float* __restrict__ lin, const float* __restrict__ tmp, const float* __restrict__ k, int w, int h, int rad,
		int bloom, int* __restrict__ outRgb, int rowStart, int rowEnd) {
	int x = blockIdx.x * blockDim.x + threadIdx.x;
	int y = rowStart + blockIdx.y * blockDim.y + threadIdx.y;
	if (x >= w || y >= rowEnd) {
		return;
	}
	int o = (y * w + x) * 3;
	float r = lin[o], g = lin[o + 1], b = lin[o + 2];
	if (bloom) {
		float br = 0.0f, bg = 0.0f, bb = 0.0f;
		for (int i = -rad; i <= rad; i++) {
			int yy = max(0, min(h - 1, y + i));
			int oo = (yy * w + x) * 3;
			float kk = k[i + rad];
			br += kk * tmp[oo];
			bg += kk * tmp[oo + 1];
			bb += kk * tmp[oo + 2];
		}
		const float strength = 0.15f;
		r = 1.0f - (1.0f - r) * (1.0f - fminf(1.0f, strength * br));
		g = 1.0f - (1.0f - g) * (1.0f - fminf(1.0f, strength * bg));
		b = 1.0f - (1.0f - b) * (1.0f - fminf(1.0f, strength * bb));
	}
	outRgb[y * w + x] = (int)((srgbByte(r) << 16) | (srgbByte(g) << 8) | srgbByte(b));
}
