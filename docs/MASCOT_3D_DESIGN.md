# Tatsu mascot: realtime plush 3D design

Status: historical initial design notes. The revised `MASCOT_3D_HANDOFF.md` and
`mascot3d-design/reference-study.js` take precedence over conflicting values below.
Astra produced and reviewed the reference geometry/material study; Sol owns implementation.
Actual Android/tablet verification is an implementation gate, not a design-study claim.

## Visual intent and identity

Source of truth: `app/src/main/res/drawable-nodpi/tatsu_mascot.png`.
Translate the existing white puppy into a small plush toy: enormous rounded head, long drooping
ears, blue oval eyes with white catchlights, pink cheeks, a tiny brown w-shaped smile, little
body/feet, arms together at the chest, curled tail visible on the viewer's left. No nose,
realistic eyeballs, teeth, human facial anatomy, or extra accessories. Cuteness takes priority
when realism conflicts with the source silhouette. Realism means physical volume, soft cloth,
rounded features and gentle illumination, not realistic animal anatomy.

The study exposed two defects corrected in the disposable study: inward-facing ear triangles caused
hollow-looking dark ears; widely separated paws lost the source's clasped-hands pose. Reverse
ear winding and bring paw centers to x ±0.185. Smooth the ear width profile and seam normals.
Do not add the source's thick flat brown outline as a tube around the 3D silhouette.

## Geometry contract

Right-handed coordinates: +Y up, +Z toward the viewer; ground y=0. Units are arbitrary.
All ellipsoid entries give center and radii, not diameter. Head-local features move with head.

| Component | Center / anchor | Radii / construction |
|---|---|---|
| Head | (0, 1.86, 0.05) | (1.02, 0.84, 0.71) |
| Body | (0, 0.75, -0.09) | (0.59, 0.65, 0.47) |
| Belly | (0, 0.71, 0.329) | (0.39, 0.43, 0.075), nearly same cloth color |
| Feet | (±0.35, 0.19, 0.19) | (0.29, 0.19, 0.37), yaw ±0.15 radians |
| Hands | (±0.185, 0.96, 0.41) | (0.19, 0.27, 0.20), roll ∓0.62 radians |
| Eye | head-local (±0.36, 0.015, surface) | (0.13, 0.174, 0.053) |
| Cheek | head-local (±0.61, -0.255, surface) | (0.165, 0.091, 0.019) |
| Ear root | head-local (±0.79, 0.35, -0.06) | flattened curved closed sweep, see below |
| Tail | root-local center (-0.68, 0.83, -0.30) | 1.025 turns, radius 0.36→0.065, tube radius 0.10 |

Head occupies about 62% of total height. Total height is 2.70, ear-to-ear extent approximately
3.9. Preserve these proportions across states: no whole-character bouncing/scaling.
Sphere tessellation 32×20 maximum in baseline; reuse geometry/materials. Small features may use
24×16 or lower. Ear sweep 32 longitudinal × 16 radial segments. No runtime mesh rebuilding.

Ear centerline relative to root, x mirrored by side:
(0,0,0), (.32,.005,-.035), (.61,-.22,-.035), (.82,-.57,-.02),
(.86,-.88,.025), (.77,-1.07,.06).
Use Catmull-Rom interpolation, width .34*sin(pi*t)^.53 and thickness width*.58.
Sweep in the XY plane with a front/back Z thickness. Close both poles, weld seam normals,
explicitly handle pole normals. Surface normals must point outwards on both mirrored ears.
Ear roots are attached to the head, not independently floating beside it.

For any face point (x,y), compute head surface:
`z = .71*sqrt(1-(x/1.02)^2-(y/.84)^2)`.
Outward normal is normalized `(x/1.02², y/.84², z/.71²)`.
Place eyes at surface + normal*.011, cheeks +normal*.008; rotate local +Z to normal.
Mount catchlights in eye-local coordinates: primary (-.037,.057,.046), radii(.032,.038,.011),
secondary (.042,-.068,.046), radii(.012,.014,.007). They blink with the eye.
Reproject **every sampled mouth point**, not only control points, to surface +normal*.018.
Closed smile controls (x,y): (-.159,-.24),(-.137,-.28),(-.09,-.294),(-.045,-.282),
(0,-.246),(.045,-.282),(.09,-.294),(.137,-.28),(.159,-.24). Tube radius .018.
Talking adds a restrained small dark mouth opening below the smile, max height .07.
Never imply phoneme synchronization: this is phase-driven animation only.

## Materials and light

White cloth: sRGB #f7f4ee, roughness .86, metalness 0. Belly #fff9f1, roughness .92.
Use one small deterministic 128² noise bump map; high-frequency contrast subtle, bump .004–.011.
No hair particles, shell fur, displacement, bloom or screen-space effects. Avoid dirty grain.
Cheeks #f2a5b9 roughness .9; mouth #785248 roughness .78. Eyes #279ed8 roughness .22,
clearcoat .7 / roughness .18, metalness 0. White catchlights are small unlit meshes.

Orthographic camera span 4.5; camera (0,2.25,7.6), target (0,1.42,0), near .1 / far 30.
No orbit controls, autonomous rotations, zooming, or hiding an eye behind the head.
Hemisphere #e6f5ff/#d8c5bc intensity1.7; key #fff5e8 intensity2.8 at(-3.5,5.5,5),
fill #cce8ff intensity.9 at(3,2.8,3), rim #ffd9e8 intensity1.8 at(2,4,-3).
sRGB output, ACES filmic, exposure1.08. Baseline uses cheap soft contact-shadow geometry;
no realtime shadow-map pass on the low-cost tablet profile. A studio-shadow study is not a
performance reference. Background is a restrained pink/blue gradient. Face must stay legible
at 196dp, without a separate expensive environment map.

## Motion specification

All changes blend over roughly 220–350ms (exponential damping with clamped delta).
Maximum head yaw 5°, roll 4°, head translation .025. Ear secondary movement ≤6°; feet stay on
the ground. Idle breath only affects torso height by ≤1%. Blink every 3.5–5.5s, duration150ms,
compress blue eye and both highlights together, never the entire head.

| VoicePhase | Pose and motion | Native status |
|---|---|---|
| IDLE | tiny breathing, slow ear settling, regular blink | 話しかけてね |
| PREPARING | mild attentive tilt; slow motion | じゅんび中… |
| LISTENING | lean/tilt gently toward viewer, eyes open, little ear lift | きいてるよ / recognized text |
| THINKING | ≤4° side tilt, slow gaze/head shift, no frenetic bounce | かんがえ中… |
| ANSWER_READY | centered calm smile | 返答を表示したよ |
| SPEAKING | restrained nod/ear follow and small mouth opening | お話し中♪ |
| ERROR | small concerned tilt, retain friendly eyes; no crying/red flashing | もう一度ためしてね |

Animation runs on the device. No AI, speech audio, network calls, audio playback, or microphone
access in the renderer. The actual VoicePhase is authoritative; never animate speaking based
on text availability alone. On pause freeze animation and stop requestAnimationFrame; on resume
reset frame timestamp so elapsed background time cannot cause a jump. Reduced-motion mode
stays in the phase pose without continuous animation.

## Android integration and failure handling

`TatsuMascot3D(phase: VoicePhase, modifier: Modifier)` in Compose. Default square224dp,
shrink to available width for narrow layouts. Keep existing native status pill, recognized text,
buttons and TalkBack description. Rendering must not consume taps/scrolls or expose WebView
controls to accessibility. Original PNG stays behind the renderer as first-frame/failure fallback.

Use pinned, locally bundled Three.js0.160.1 (MIT; include license), with WebGL1 support for older
WebViews. Offline WebView on `https://appassets.androidplatform.net/assets/mascot3d/` through
WebViewAssetLoader. No CDN/runtime download. Whitelist exact asset paths; deny all other
requests/navigation, file/content access, popups, storage, mixed content. CSP connect-src none.
No addJavascriptInterface. Native sends **only enum-derived** phase and booleans through
`evaluateJavascript`; never interpolate recognized text or model output into JS/HTML.

JS API: `window.tatsuMascot.setPhase(name)`, `.setPaused(boolean)`, `.dispose()`, `.ready`, `.failed`.
ready becomes true only after first successful render. Host polls boundedly during startup;
missing/failed initialization, JS load errors, WebGL loss or renderer-process death show PNG.
WebGL context loss stops the loop. Destroyed instances do not restart. Handle native lifecycle
and viewport visibility independently; run only when resumed and visible. Do not call global
WebView.pauseTimers (could affect unrelated WebViews). Dispose geometry, materials, textures,
RAF, observers/listeners and WebGL renderer; then destroy native view on composition removal.

## Cost/performance budget and acceptance

Baseline target: NEC LAVIE T8 PC-T0875CAS / Helio P22T-class, Android11; min SDK remains28.
Design budget **not yet a real-device measurement**: 24fps maximum, DPR≤1.5, canvas side≤640px,
≤35 draw calls and ≤35k submitted triangles/frame in the baseline with no shadow pass.
No network after installation, no per-frame geometry/material/texture allocation.
Run only while visible/resumed. GPU memory and thermal behavior require the actual tablet.

Review at224dp and640px: front, yaw±5°, every phase, eye mid-blink, maximum ear movement.
Pass if both eyes remain visible, mouth/cheeks never sink or float, ears remain smooth/closed,
paws clasp, feet stay grounded, no surface flicker/seam, tail stays behind body, and character
reads as the existing mascot. Also inspect yaw±20° diagnostically (outside production poses).

Integration acceptance: all7enum states; repeated phase changes; background/resume; scrolled
out of view; rotation/recomposition; no WebGL/blocked assets/context loss -> PNG + working
native controls; TalkBack uses native description; no unsolicited browser requests. Build/test
Android with existing CI. Browser checks validate geometry/control flow, not Android WebView
lifecycle, real microphone behavior, mobile fps, power draw or measured wake-word accuracy.

## Review gates still open

- Astra must review this synthesis and settle final geometry/material/motion values.
- The study demonstrates volume and recognizability, but does not yet prove convincing plush
  softness: refine cloth response at 196/224dp without expensive fur or visible noisy grain.
- Tail currently reads partly as a loop/handle; review spiral visibility and body occlusion.
- Inspect ear poles and seam normals at front and ±20°; do not accept pinched tips or creases.
- Verify face placement and silhouette in all seven poses and a mid-blink, not just idle.
- Frame budget is a target; the disposable browser study is not mobile performance proof.
- Android lifecycle, fallback and access restrictions are implementation requirements, not
  implemented or tested guarantees in this design-only task.
