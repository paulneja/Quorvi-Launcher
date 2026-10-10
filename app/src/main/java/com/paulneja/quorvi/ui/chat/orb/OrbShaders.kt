package com.paulneja.quorvi.ui.chat.orb

import android.graphics.RuntimeShader
import android.os.Build

internal object OrbShaders {
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun pearl(): RuntimeShader? = make(COMMON + PEARL)

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun singularity(): RuntimeShader? = make(COMMON + SINGULARITY)

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun nova(): RuntimeShader? = make(NOVA)

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun make(source: String): RuntimeShader? =
        runCatching { RuntimeShader(source) }.onFailure { android.util.Log.w("NexoOrb", "shader: ${it.message}") }.getOrNull()

    private const val COMMON = """
uniform float2 size;
uniform float time;
uniform float level;
uniform float listen;
uniform float think;
uniform float speak;
uniform float work;
uniform float2 tilt;
uniform float3 accent;
uniform float detail;
uniform float intro;

float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        if (float(i) >= detail) { break; }
        v += a * noise(p);
        p = p * 2.03 + float2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

float2 rot(float2 p, float a) {
    float c = cos(a);
    float s = sin(a);
    return float2(c * p.x - s * p.y, s * p.x + c * p.y);
}
"""

    private const val PEARL = """
half4 main(float2 fc) {
    float scale = min(size.x, size.y) * 0.5;
    float2 uv = (fc - size * 0.5) / scale;
    float d = length(uv);
    float px = 2.0 / scale;
    float3 violet = float3(0.725, 0.55, 1.0);
    float3 cyan = float3(0.36, 0.88, 0.90);
    float3 deep = float3(0.035, 0.045, 0.11);

    float R = 0.74 * intro * (1.0 + 0.05 * speak * level + 0.035 * listen * level);
    R += listen * level * 0.015 * sin(atan(uv.y, uv.x) * 5.0 + time * 5.0) + listen * level * 0.010 * sin(d * 30.0 - time * 9.0);

    float3 col = float3(0.0);
    float alpha = 0.0;
    if (d < R + px) {
        float z = sqrt(max(R * R - d * d, 0.0));
        float3 n = normalize(float3(uv, z));
        float2 q = uv * 1.15 + n.xy * 0.45 + tilt * 0.22;
        float collapse = 1.0 - intro;
        float swirl = think * 3.2 * (1.0 - d / max(R, 0.001)) + time * (0.045 + 0.25 * think)
            + collapse * collapse * 28.0 * (1.0 - d / max(R, 0.001));
        q = rot(q, swirl);
        float2 w1 = float2(fbm(q + time * 0.07), fbm(q + float2(5.2, 1.3) - time * 0.06));
        float2 w2 = float2(fbm(q + 3.0 * w1 + float2(1.7, 9.2) + time * 0.12), fbm(q + 3.0 * w1 + float2(8.3, 2.8) - time * 0.10));
        float f = fbm(q + 3.4 * w2);

        float fr = 1.0 - n.z;
        float3 base = mix(deep * 1.4 + accent * 0.22, violet * 0.9, smoothstep(0.30, 0.80, f));
        col = mix(base, cyan * 0.95, smoothstep(0.52, 0.95, w2.y) * 0.55);
        col = mix(col, accent * 1.2, smoothstep(0.58, 0.95, length(w1)) * 0.5);
        col += pow(smoothstep(0.50, 1.0, f), 2.0) * 1.05;
        float3 irid = 0.5 + 0.5 * cos(6.2831 * (f * 0.55 + fr * 0.9 + time * 0.025 + float3(0.0, 0.33, 0.67)));
        col = mix(col, irid, 0.05 + 0.42 * pow(fr, 1.6));
        col += accent * pow(1.0 - d / max(R, 0.001), 2.0) * (0.22 + 0.35 * think + 0.55 * speak * level);
        col += cyan * speak * level * pow(0.5 + 0.5 * sin(d * 18.0 - time * 11.0), 4.0) * 0.45 * (1.0 - d / max(R, 0.001));

        float3 L = normalize(float3(-0.42 + tilt.x * 0.7, -0.55 + tilt.y * 0.7, 0.72));
        float diff = max(dot(n, L), 0.0);
        float spec = pow(max(dot(reflect(-L, n), float3(0.0, 0.0, 1.0)), 0.0), 42.0);
        col *= 0.78 + 0.40 * diff;
        col += spec * 1.1;
        col += pow(max(dot(n, normalize(float3(0.5, 0.65, 0.6))), 0.0), 18.0) * 0.18 * cyan;
        col += float3(pow(fr, 2.6) * 0.30, pow(fr, 3.0) * 0.32, pow(fr, 2.2) * 0.55);
        col += collapse * pow(f, 2.0) * 2.2;
        col += collapse * 1.8 * exp(-d / max(R, 0.02) * 3.0);
        alpha = smoothstep(R + px, R - px, d);
    }

    float halo = exp(-max(d - R, 0.0) * 6.5) * (0.30 + 0.25 * think + 0.40 * level * (listen + speak)) * intro;
    float3 haloCol = mix(accent, violet, 0.35 + 0.25 * sin(time * 0.4));

    float sparks = 0.0;
    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        float a = time * (1.1 + fi * 0.27) + fi * 1.7;
        float2 sp = float2(cos(a), sin(a)) * R * (1.10 + 0.06 * sin(time * 2.0 + fi));
        sparks += smoothstep(0.035, 0.0, length(uv - sp)) * (0.6 + 0.4 * sin(time * 9.0 + fi));
    }
    float3 sparkCol = mix(cyan, float3(1.0), 0.4) * sparks * work;

    float edge = smoothstep(1.0, 0.80, d);
    float3 outside = (haloCol * halo + sparkCol) * edge;
    float outA = clamp(max(outside.r, max(outside.g, outside.b)), 0.0, 1.0);
    float3 rgb = col * alpha + outside * (1.0 - alpha);
    float outAlpha = alpha + outA * (1.0 - alpha);
    return half4(half3(min(rgb, float3(outAlpha))), half(outAlpha));
}
"""

    private const val SINGULARITY = """
half4 main(float2 fc) {
    float scale = min(size.x, size.y) * 0.5;
    float2 uv = (fc - size * 0.5) / scale;
    float px = 2.0 / scale;
    float3 pink = float3(1.0, 0.373, 0.635);
    float3 orange = float3(1.0, 0.635, 0.298);
    float3 violet = float3(0.608, 0.424, 1.0);
    float3 cyan = float3(0.302, 0.882, 1.0);

    float grow = intro;
    float edge = smoothstep(1.0, 0.80, length(uv));
    uv *= 0.8;
    float ign = 1.0 - grow;
    float d = length(uv);
    float rc = 0.24 * (1.0 + 0.10 * think + 0.06 * speak * level) * (0.35 + 0.65 * smoothstep(0.0, 0.6, grow));
    float spin = time * (0.55 + 1.6 * think);

    float k = clamp(0.30 + 0.14 * tilt.y - 0.12 * listen, 0.12, 0.6);
    float2 dp = float2(uv.x - tilt.x * 0.08, uv.y / k);
    float r = length(dp);
    float a = atan(dp.y, dp.x);
    float a2 = a + spin * (0.30 / (r + 0.06)) + ign * ign * 14.0 / (r + 0.2);
    float2 pp = float2(cos(a2), sin(a2)) * r * 3.2;
    float n1 = fbm(pp + float2(time * 0.05, 0.0));
    float n2 = fbm(float2(r * 14.0 - time * 0.6, 0.0) + pp * 0.45);
    float reach = 0.25 + 0.85 * grow;
    float density = smoothstep(rc * 1.05, rc * 1.55, r) * smoothstep(reach, reach * 0.55, r) * (0.30 + 0.95 * n1) * (0.55 + 0.6 * n2);
    float3 disk = mix(pink, orange, smoothstep(0.62, 0.30, r));
    disk = mix(disk, violet, smoothstep(0.55, 0.90, r));
    disk = mix(disk, cyan, n2 * 0.55 * smoothstep(0.65, 0.95, r));
    float beam = 1.0 + 0.65 * cos(a - spin * 0.2);
    float3 diskCol = disk * density * beam * (1.0 + 0.6 * think);
    bool front = dp.y > 0.0;

    float ang = atan(uv.y, uv.x);
    float2 cp = float2(cos(ang), sin(ang)) * 2.2;
    float flare = fbm(cp + float2(time * 0.22, -time * 0.17));
    float corona = exp(-max(d - rc, 0.0) * (3.6 - 1.2 * speak * level - 1.5 * ign)) * (0.25 + 0.95 * flare * flare) * (0.55 + 0.9 * speak * level + 0.35 * think + 2.5 * ign * grow);
    float3 col = mix(violet, pink, flare) * corona;

    float ring = exp(-pow((d - rc * 1.22) / 0.035, 2.0)) * (0.55 + 0.6 * fbm(float2(ang * 3.0, time * 0.4)));
    col += mix(orange, float3(1.0, 0.9, 0.8), 0.45) * ring;
    if (!front) { col += diskCol * smoothstep(rc * 0.98, rc * 1.08, d); }

    col += violet * listen * level * (0.5 + 0.5 * sin(d * 30.0 - time * 6.0)) * exp(-d * 2.2);

    float core = smoothstep(rc + px, rc - px, d);
    col = mix(col, float3(0.012, 0.008, 0.025) + violet * 0.04 * (1.0 - d / rc), core);
    col += float3(1.0, 0.92, 0.95) * exp(-pow((d - rc) / 0.012, 2.0)) * 0.9;
    float coreA = core;

    if (front) { col += diskCol; }

    for (int i = 0; i < 22; i++) {
        float fi = float(i);
        float h1 = hash(float2(fi, 1.7));
        float h2 = hash(float2(fi, 8.3));
        float h3 = hash(float2(fi, 4.1));
        float ro = (0.42 + h1 * 0.50) * (1.0 + 0.25 * speak * level) * (0.3 + 0.7 * grow);
        float incl = (h2 - 0.5) * 1.6 + tilt.y * 0.4;
        float t = h3 * 6.283 + time * (0.35 + h1 * 0.5) * (1.0 + 2.2 * think) * (h2 > 0.5 ? 1.0 : -1.0);
        float3 p3 = float3(cos(t) * ro, 0.0, sin(t) * ro);
        p3 = float3(p3.x, p3.z * sin(incl), p3.z * cos(incl));
        float persp = 1.0 / (1.55 - p3.z * 0.55);
        float2 sp = p3.xy * persp;
        float behind = (p3.z < 0.0 && length(sp) < rc) ? 0.0 : 1.0;
        float sz = 0.010 * persp * (1.0 + 1.5 * speak * level);
        float dist = length(uv - sp);
        if (dist < sz * 2.6) {
            float glow = smoothstep(sz * 2.6, 0.0, dist) * behind * (p3.z < 0.0 ? 0.45 : 1.0);
            col += mix(cyan, orange, h3) * glow * 1.4;
        }
    }

    for (int j = 0; j < 3; j++) {
        if (work < 0.01) { break; }
        float fj = float(j);
        float strike = floor(time * 3.0 + fj * 0.37);
        float th = hash(float2(strike, fj)) * 6.283;
        float2 dir = float2(cos(th), sin(th));
        float along = dot(uv, dir);
        float side = dot(uv, float2(-dir.y, dir.x));
        float jag = (fbm(float2(along * 9.0, strike * 3.1 + fj)) - 0.5) * 0.16;
        float onArc = smoothstep(rc * 0.9, rc * 1.1, along) * smoothstep(rc + 0.42, rc + 0.25, along);
        float flick = 0.6 + 0.4 * sin(time * 40.0 + fj * 5.0);
        col += mix(cyan, float3(1.0), 0.5) * exp(-abs(side - jag) * 110.0) * onArc * work * flick * 1.3;
    }

    col *= smoothstep(0.0, 0.25, grow) * edge;
    col += ign * 1.6 * exp(-d * 7.0) * float3(1.0, 0.9, 0.95) * edge;
    float outA = clamp(max(coreA * grow, max(col.r, max(col.g, col.b))), 0.0, 1.0);
    return half4(half3(min(col, float3(outA))), half(outA));
}
"""

    private const val NOVA = """
uniform float2 size;
uniform float orbR;
uniform float p;
uniform float time;

float h1(float n) { return fract(sin(n * 127.1) * 43758.5453); }

float bell(float x, float a, float b) {
    float t = clamp((x - a) / (b - a), 0.0, 1.0);
    return sin(t * 3.14159);
}

half4 main(float2 fc) {
    float2 uv = (fc - size * 0.5) / orbR;
    float r = length(uv);
    float ang = atan(uv.y, uv.x);
    float3 cyan = float3(0.36, 0.88, 0.95);
    float3 violet = float3(0.66, 0.5, 1.0);
    float3 pink = float3(1.0, 0.37, 0.64);
    float3 orange = float3(1.0, 0.64, 0.30);
    float3 col = float3(0.0);

    float infall = bell(p, 0.04, 0.52);
    if (infall > 0.0) {
        float n = 40.0;
        float kc = floor((ang + 3.14159) / 6.2831 * n);
        for (int i = 0; i < 3; i++) {
            float fi = mod(kc + float(i) - 1.0 + n, n);
            float speed = 0.8 + h1(fi + 7.0) * 1.4;
            float s = fract(h1(fi + 3.0) + p * speed * 1.6);
            float rs = 3.6 * (1.0 - s) + 0.15;
            float base = (fi + 0.5) / n * 6.2831 - 3.14159 + (h1(fi) - 0.5) * 0.08;
            float a = base + 0.06 / (rs + 0.25) * p;
            float da = abs(mod(ang - a + 3.14159, 6.2831) - 3.14159);
            float streak = exp(-pow(da * r * 28.0, 2.0)) * exp(-pow((r - rs) * 2.2, 2.0)) * smoothstep(0.1, 0.6, r);
            col += mix(cyan, violet, h1(fi + 11.0)) * streak * (0.4 + 1.2 * s);
        }
        float well = 0.5 + 0.5 * sin(r * 9.0 + p * 40.0);
        col += violet * pow(well, 8.0) * exp(-r * 0.9) * 0.22 * infall * smoothstep(0.3, 1.2, r);
        col *= infall;
    }

    float flash = bell(p, 0.44, 0.60);
    col += float3(1.0) * exp(-r * (6.0 - 3.0 * flash)) * flash * 2.4;
    col += float3(exp(-pow((r - 0.55 * flash) * 7.0, 2.0)), exp(-pow((r - 0.50 * flash) * 7.0, 2.0)), exp(-pow((r - 0.45 * flash) * 7.0, 2.0))) * flash;

    col += mix(violet, pink, 0.5) * 0.35 * bell(p, 0.47, 0.66) * exp(-r * 0.45);

    float sw = clamp((p - 0.50) / 0.45, 0.0, 1.0);
    if (sw > 0.0) {
        float e = 1.0 - pow(1.0 - sw, 3.0);
        float rs = 0.2 + 4.2 * e;
        float fade = pow(1.0 - sw, 1.4);
        float w = 0.035 + 0.07 * sw;
        float front = exp(-pow((r - rs) / w, 2.0));
        float outer = exp(-pow((r - rs - w * 0.9) / (w * 0.55), 2.0));
        float inner = exp(-pow((r - rs + w * 0.9) / (w * 0.55), 2.0));
        col += (float3(1.0, 0.92, 0.97) * front * 1.4 + pink * outer * 0.7 + cyan * inner * 0.55) * fade;
        col += mix(violet, pink, 0.35) * smoothstep(rs, rs * 0.55, r) * smoothstep(rs * 0.2, rs * 0.7, r) * fade * 0.28;
        float rs2 = 0.1 + 2.8 * (1.0 - pow(1.0 - clamp((p - 0.56) / 0.4, 0.0, 1.0), 2.0));
        col += mix(pink, violet, 0.5) * exp(-pow((r - rs2) / (0.25 + 0.3 * sw), 2.0)) * fade * 0.55;
        float nd = 34.0;
        float kd = floor((ang + 3.14159) / 6.2831 * nd);
        for (int i = 0; i < 3; i++) {
            float fi = mod(kd + float(i) - 1.0 + nd, nd);
            float a = (fi + 0.5) / nd * 6.2831 - 3.14159 + (h1(fi + 21.0) - 0.5) * 0.1;
            float v = 1.0 + h1(fi + 5.0) * 3.0;
            float rd = 0.2 + v * (1.0 - pow(1.0 - sw, 2.2));
            float da = abs(mod(ang - a + 3.14159, 6.2831) - 3.14159);
            float trail = exp(-pow(da * r * 26.0, 2.0)) * smoothstep(rd - 0.6 * (1.0 - sw), rd, r) * smoothstep(rd + 0.06, rd, r);
            col += mix(orange, pink, h1(fi + 9.0)) * trail * fade * 2.6;
        }
    }

    float jet = bell(p, 0.50, 0.92);
    if (jet > 0.0) {
        float len = 0.4 + 3.4 * clamp((p - 0.5) / 0.3, 0.0, 1.0);
        float ay = abs(uv.y);
        float width = 0.07 + 0.18 * ay / len;
        float core = exp(-pow(uv.x / (width * 0.35), 2.0));
        float sheath = exp(-pow(uv.x / width, 2.0));
        float along = smoothstep(len, len * 0.55, ay) * smoothstep(0.05, 0.3, ay);
        float knots = 0.6 + 0.4 * sin(ay * 12.0 - time * 34.0);
        col += (float3(1.0, 0.95, 1.0) * core * 1.8 + mix(cyan, pink, clamp(ay / len, 0.0, 1.0)) * sheath) * along * knots * jet * 1.6;
    }

    float edge = smoothstep(min(size.x, size.y) * 0.5 / orbR, min(size.x, size.y) * 0.5 / orbR * 0.82, r);
    col *= edge;
    float a = clamp(max(col.r, max(col.g, col.b)), 0.0, 1.0);
    return half4(half3(min(col, float3(a))), half(a));
}
"""
}
