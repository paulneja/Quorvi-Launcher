package com.paulneja.quorvi.ui.chat.horizon

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi

internal object HorizonShaders {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun warp(): RuntimeShader? = make(WARP)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun field(): RuntimeShader? = make(FIELD)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun make(source: String): RuntimeShader? =
        runCatching { RuntimeShader(source) }.onFailure { android.util.Log.w("Horizon", "shader: ${it.message}") }.getOrNull()

    private const val WARP = """
uniform shader content;
uniform float2 size;
uniform float2 center;
uniform float w;
uniform float reach;
uniform float orbR;
uniform float spin;
uniform float3 tint;

half4 tap(float R, float a) {
    float2 q = center + R * float2(cos(a), sin(a));
    if (q.x < 0.0 || q.y < 0.0 || q.x > size.x || q.y > size.y) { return half4(0.0); }
    return content.eval(q);
}

half4 main(float2 fc) {
    float2 v = fc - center;
    float r = length(v);
    float a = atan(v.y, v.x);
    float e = w * w * (3.0 - 2.0 * w);
    float D = pow(w, 1.25) * reach * 1.12;
    float R = sqrt(r * r + D * D);
    float aa = a + spin * 6.5 * e * exp(-r / (reach * 0.2)) + spin * 0.6 * e;
    float smear = 34.0 * e;
    half4 mid = tap(R, aa) * 0.42 + tap(R + smear, aa) * 0.33 + tap(R + 2.2 * smear, aa) * 0.25;
    float split = 9.0 * e;
    half4 red = tap(R + split, aa);
    half4 blue = tap(max(R - split, 0.0), aa);
    half4 col = half4(red.r, mid.g, blue.b, max(mid.a, max(red.a, blue.a) * 0.6));
    col.rgb = min(col.rgb, half3(col.a));
    col *= half(1.0 - 0.8 * e * exp(-r / (orbR * 2.6)));
    float ring = e * exp(-pow((r - orbR * 1.18) / (orbR * 0.12), 2.0)) * 0.55;
    float halo = e * e * exp(-r / (orbR * 3.0)) * 0.10;
    float3 light = mix(float3(1.0), tint, 0.45) * ring + tint * halo;
    float g = ring + halo;
    return col + half4(half3(light), half(min(g, 1.0) * 0.85));
}
"""

    private const val FIELD = """
uniform float2 size;
uniform float2 center;
uniform float orbR;
uniform float time;
uniform float mode;
uniform float w;
uniform float spin;
uniform float2 tilt;
uniform float think;
uniform float energy;
uniform float3 accent;
uniform float3 top;
uniform float3 bottom;
uniform float dark;
uniform float presence;
uniform float voice;
uniform float4 rp0;
uniform float4 rp1;
uniform float4 rp2;
uniform float4 rp3;

float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}


float wave(float2 p) {
    float a = sin(p.x * 1.7 + sin(p.y * 1.3 + 0.7));
    float b = sin(p.y * 1.9 + sin(p.x * 1.1 - 1.3));
    float c = sin((p.x + p.y) * 0.9 + sin(p.x * 0.6 - p.y * 0.8));
    return 0.5 + (a + b + c) / 6.0;
}

float stars(float2 s, float cell, float seed) {
    float2 id = floor(s / cell);
    float h = hash(id + seed);
    if (h < 0.55) { return 0.0; }
    float2 pos = (id + 0.2 + 0.6 * float2(hash(id + seed + 3.1), hash(id + seed + 7.7))) * cell;
    float d = length(s - pos);
    float rad = cell * (0.018 + 0.03 * hash(id + seed + 1.3));
    float tw = 0.65 + 0.35 * sin(time * (0.8 + 2.0 * h) + h * 40.0);
    return exp(-d * d / (rad * rad)) * (h - 0.5) * 2.2 * tw;
}

float ringAt(float4 rp, float2 fc) {
    if (rp.w <= 0.0) { return 0.0; }
    float age = time - rp.z;
    if (age < 0.0 || age > 2.5) { return 0.0; }
    float d = length(fc - rp.xy);
    float rad = age * orbR * 4.2;
    float wd = orbR * (0.25 + 0.35 * age);
    float x = (d - rad) / wd;
    return exp(-x * x) * exp(-age * 1.7) * rp.w;
}

float2 pushAt(float4 rp, float2 fc) {
    float2 d = fc - rp.xy;
    return d / (length(d) + 1.0) * ringAt(rp, fc);
}

half4 main(float2 fc) {
    float rip = ringAt(rp0, fc) + ringAt(rp1, fc) + ringAt(rp2, fc) + ringAt(rp3, fc);
    float2 push = pushAt(rp0, fc) + pushAt(rp1, fc) + pushAt(rp2, fc) + pushAt(rp3, fc);
    float2 v = fc - center + push * orbR * 0.45;
    float e = w * w * (3.0 - 2.0 * w);
    float r0 = length(v);
    float twist = spin * 2.4 * e * exp(-r0 / (max(size.x, size.y) * 0.5));
    float c = cos(twist);
    float s0 = sin(twist);
    v = float2(c * v.x - s0 * v.y, s0 * v.x + c * v.y) * (1.0 + 1.6 * e);
    float r = length(v) / orbR;
    float a = atan(v.y, v.x);

    float3 base = mix(top, bottom, clamp(fc.y / size.y, 0.0, 1.0));
    float3 ink = dark > 0.5 ? float3(1.0) : float3(-0.55);

    float n = wave(v / orbR * 0.55 + float2(time * 0.04, -time * 0.03));
    float rings = 0.5 + 0.5 * sin(r * 1.9 - time * (0.7 + 1.4 * energy) + n * 3.4 - voice * 2.5);
    float arms = 0.5 + 0.5 * sin(a * 3.0 + log(r + 0.4) * 4.5 - time * 2.2);
    float3 iri = 0.5 + 0.5 * cos(6.2831 * (float3(0.0, 0.33, 0.67) + r * 0.06 + n * 0.7 + time * 0.025));
    float3 nacre = mix(accent, iri, 0.55);
    float fall = exp(-r * 0.36);
    float chat = fall * (0.30 + 0.70 * pow(mix(rings, arms, think * 0.75), 3.0)) * (0.20 + 0.08 * energy);
    chat += exp(-r * 0.9) * 0.14;
    chat *= 1.0 + 1.4 * voice;

    float E = 1.75;
    float2 src = v * (1.0 - E * E / max(r * r, 0.05)) + tilt * orbR * 0.35;
    float st = stars(src, orbR * 0.62, 0.0) + stars(src * 1.7 + 31.0, orbR * 0.62, 5.0) * 0.6;
    st *= smoothstep(0.85, 1.35, r);
    float neb = wave(src / orbR * 0.18) * wave(src / orbR * 0.07 + 4.0);
    float3 pink = float3(1.0, 0.37, 0.64);
    float3 orange = float3(1.0, 0.64, 0.30);
    float3 violet = float3(0.6, 0.45, 1.0);
    float3 disk = mix(orange, pink, 0.5 + 0.5 * sin(a * 2.0 + time * 0.4));
    float3 ultra = float3(st) * 0.9
        + mix(violet, pink, neb) * neb * 0.10
        + disk * exp(-pow((r - E) / 0.55, 2.0)) * (0.10 + 0.25 * voice)
        + violet * exp(-r * 0.45) * 0.07;
    float3 ultraBase = mix(base * 0.55, base, 1.0 - dark);

    float3 col = mix(base + ink * nacre * chat * presence, ultraBase + ink * ultra * mix(0.75, 1.0, presence), mode);
    col += ink * mix(accent, pink, mode) * e * exp(-r * 0.5) * 0.18;
    col += ink * mix(nacre, mix(violet, pink, 0.5), mode) * rip * 0.16;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""
}
