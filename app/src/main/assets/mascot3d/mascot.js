import * as T from "./three.module.min.js";
const api = (window.tatsuMascot = { ready: false, failed: false });
let disposed = false,
  paused = true,
  raf = 0,
  last = 0,
  elapsed = 0,
  phase = "IDLE";
let renderer;
try {
  const scene = new T.Scene();
  renderer = new T.WebGLRenderer({
    antialias: true,
    alpha: true,
    powerPreference: "low-power",
  });
  renderer.setPixelRatio(
    Math.min(
      devicePixelRatio || 1,
      1.5,
      640 / Math.max(innerWidth, innerHeight),
    ),
  );
  renderer.setSize(innerWidth, innerHeight);
  renderer.outputColorSpace = T.SRGBColorSpace;
  renderer.toneMapping = T.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.08;
  renderer.shadowMap.enabled = false;
  renderer.shadowMap.type = T.PCFSoftShadowMap;
  document.body.appendChild(renderer.domElement);
  const aspect = innerWidth / innerHeight;
  const v = 4.35;
  const camera = new T.OrthographicCamera(
    (-v * aspect) / 2,
    (v * aspect) / 2,
    v / 2,
    -v / 2,
    0.1,
    30,
  );
  camera.position.set(0, 2.25, 7.6);
  camera.lookAt(0, 1.42, 0);
  scene.add(new T.HemisphereLight(0xe6f5ff, 0xd8c5bc, 1.7));
  const key = new T.DirectionalLight(0xfff5e8, 2.8);
  key.position.set(-3.5, 5.5, 5);
  key.castShadow = true;
  key.shadow.mapSize.set(1024, 1024);
  key.shadow.camera.left = -3;
  key.shadow.camera.right = 3;
  key.shadow.camera.top = 4;
  key.shadow.camera.bottom = -2;
  key.shadow.normalBias = 0.025;
  key.shadow.bias = -0.00025;
  key.shadow.radius = 3;
  scene.add(key);
  const fill = new T.DirectionalLight(0xcce8ff, 0.9);
  fill.position.set(3, 2.8, 3);
  scene.add(fill);
  const rim = new T.DirectionalLight(0xffd9e8, 1.8);
  rim.position.set(2, 4, -3);
  scene.add(rim);
  const white = new T.MeshPhysicalMaterial({
    color: 0xf9f5ef,
    roughness: 0.96,
    metalness: 0,
    sheen: 0.65,
    sheenColor: 0xffffff,
    sheenRoughness: 1,
  });

  const pink = new T.MeshStandardMaterial({ color: 0xf2a5b9, roughness: 0.9 });
  const eyeMat = new T.MeshPhysicalMaterial({
    color: 0x269cd2,
    roughness: 0.38,
    metalness: 0,
    clearcoat: 0.22,
    clearcoatRoughness: 0.35,
  });
  const brown = new T.MeshStandardMaterial({
    color: 0x785248,
    roughness: 0.78,
  });
  const highlight = new T.MeshBasicMaterial({ color: 0xffffff });
  const sphere = new T.SphereGeometry(1, 32, 20);
  function mesh(parent, mat, p, s, geom = sphere) {
    const m = new T.Mesh(geom, mat);
    m.position.set(...p);
    m.scale.set(...s);
    m.castShadow = true;
    m.receiveShadow = true;
    parent.add(m);
    return m;
  }
  const root = new T.Group();
  scene.add(root);
  const body = mesh(root, white, [0, 0.75, -0.09], [0.59, 0.65, 0.47]);
  // Body stays a single soft shape: no hard belly plate.
  const feet = [];
  for (const sign of [-1, 1]) {
    const f = mesh(root, white, [sign * 0.35, 0.19, 0.19], [0.29, 0.19, 0.37]);
    f.rotation.y = sign * 0.15;
    feet.push(f);
    const paw = mesh(
      root,
      white,
      [sign * 0.185, 0.96, 0.41],
      [0.19, 0.27, 0.2],
    );
    paw.rotation.z = sign * -0.62;
  }
  const head = new T.Group();
  head.position.set(0, 1.86, 0.05);
  root.add(head);
  mesh(head, white, [0, 0, 0], [1.02, 0.84, 0.71]);
  // Surface-following local mounts are essential: facial details must clear the curved head.
  function surface(x, y, offset = 0) {
    const z =
      0.71 * Math.sqrt(Math.max(0, 1 - (x / 1.02) ** 2 - (y / 0.84) ** 2));
    const n = new T.Vector3(
      x / 1.02 ** 2,
      y / 0.84 ** 2,
      z / 0.71 ** 2,
    ).normalize();
    return { p: new T.Vector3(x, y, z).addScaledVector(n, offset), n };
  }
  const eyes = [];
  for (const sign of [-1, 1]) {
    const mount = surface(sign * 0.36, 0.015, 0.004);
    const g = new T.Group();
    g.position.copy(mount.p);
    g.quaternion.setFromUnitVectors(new T.Vector3(0, 0, 1), mount.n);
    head.add(g);
    mesh(g, eyeMat, [0, 0, 0], [0.13, 0.174, 0.03]);
    mesh(g, highlight, [-0.035, 0.059, 0.027], [0.03, 0.035, 0.004]);
    mesh(g, highlight, [0.04, -0.068, 0.024], [0.01, 0.013, 0.003]);
    eyes.push(g);
    const ch = surface(sign * 0.61, -0.255, 0.008);
    const c = mesh(head, pink, [ch.p.x, ch.p.y, ch.p.z], [0.165, 0.091, 0.019]);
    c.quaternion.setFromUnitVectors(new T.Vector3(0, 0, 1), ch.n);
  }
  function tube(parent, pts, r, mat, segments = 48) {
    const c = new T.CatmullRomCurve3(pts.map((v) => new T.Vector3(...v)));
    return mesh(
      parent,
      mat,
      [0, 0, 0],
      [1, 1, 1],
      new T.TubeGeometry(c, segments, r, 8, false),
    );
  }
  const mouth2d = [
    [-0.159, -0.24],
    [-0.137, -0.28],
    [-0.09, -0.294],
    [-0.045, -0.282],
    [0, -0.246],
    [0.045, -0.282],
    [0.09, -0.294],
    [0.137, -0.28],
    [0.159, -0.24],
  ];
  const mouthPath = new T.CatmullRomCurve3(
    mouth2d.map(([x, y]) => new T.Vector3(x, y, 0)),
  );
  class FaceCurve extends T.Curve {
    getPoint(t, target = new T.Vector3()) {
      const p = mouthPath.getPoint(t);
      return target.copy(surface(p.x, p.y, 0.018).p);
    }
  }
  mesh(
    head,
    brown,
    [0, 0, 0],
    [1, 1, 1],
    new T.TubeGeometry(new FaceCurve(), 48, 0.018, 8, false),
  );
  // Long floppy ears are tapered, flattened sweeps: their centerlines never point upward.
  function ear(sign) {
    const g = new T.Group();
    g.position.set(sign * 0.79, 0.35, -0.06);
    head.add(g);
    const anchors = [
      [0, 0, 0],
      [0.3, -0.03, -0.02],
      [0.55, -0.2, -0.04],
      [0.72, -0.43, -0.02],
      [0.8, -0.68, 0.01],
      [0.79, -0.97, 0.035],
    ];
    const c = new T.CatmullRomCurve3(
      anchors.map((p) => new T.Vector3(sign * p[0], p[1], p[2])),
    );
    const pos = [],
      uv = [],
      ix = [];
    const rows = 32,
      cols = 16;
    for (let j = 0; j <= rows; j++) {
      const t = j / rows,
        p = c.getPoint(t),
        tan = c.getTangent(t);
      const n = new T.Vector3(-tan.y, tan.x, 0).normalize();
      const w = 0.31 * Math.pow(Math.sin(Math.PI * t), 0.5),
        d = w * 0.58;
      for (let k = 0; k <= cols; k++) {
        const a = (k / cols) * Math.PI * 2;
        pos.push(
          p.x + n.x * Math.cos(a) * w,
          p.y + n.y * Math.cos(a) * w,
          p.z + Math.sin(a) * d,
        );
        uv.push(k / cols, t);
        if (j < rows && k < cols) {
          const a1 = j * (cols + 1) + k,
            b = a1 + cols + 1;
          ix.push(a1, a1 + 1, b, b, a1 + 1, b + 1);
        }
      }
    }
    const geo = new T.BufferGeometry();
    geo.setAttribute("position", new T.Float32BufferAttribute(pos, 3));
    geo.setAttribute("uv", new T.Float32BufferAttribute(uv, 2));
    geo.setIndex(ix);
    geo.computeVertexNormals();
    const normals = geo.getAttribute("normal");
    for (let j = 1; j < rows; j++) {
      const ia = j * (cols + 1),
        ib = ia + cols;
      const n = new T.Vector3()
        .fromBufferAttribute(normals, ia)
        .add(new T.Vector3().fromBufferAttribute(normals, ib))
        .normalize();
      normals.setXYZ(ia, n.x, n.y, n.z);
      normals.setXYZ(ib, n.x, n.y, n.z);
    }
    for (const j of [0, rows]) {
      const n = c
        .getTangent(j / rows)
        .multiplyScalar(j === 0 ? -1 : 1)
        .normalize();
      for (let k = 0; k <= cols; k++)
        normals.setXYZ(j * (cols + 1) + k, n.x, n.y, n.z);
    }
    normals.needsUpdate = true;
    mesh(g, white, [0, 0, 0], [1, 1, 1], geo);
    return g;
  }
  const ears = [ear(-1), ear(1)];
  // The spiral is behind the body, readable at the left edge without looking like a handle.
  const tailPoints = [];
  for (let i = 0; i <= 72; i++) {
    const t = i / 72,
      a = -0.2 + t * Math.PI * 2.35,
      r = 0.31 * (1 - t) + 0.045 * t;
    tailPoints.push([
      -0.86 + Math.cos(a) * r,
      0.77 + Math.sin(a) * r,
      -0.2 + 0.025 * t,
    ]);
  }
  tube(root, tailPoints, 0.07, white, 72);
  mesh(root, white, tailPoints[tailPoints.length - 1], [0.07, 0.07, 0.07]);
  // Small offline contact shadow, no extra shadow render pass.
  const shadowCanvas = document.createElement("canvas");
  shadowCanvas.width = shadowCanvas.height = 64;
  const shadowContext = shadowCanvas.getContext("2d"),
    gradient = shadowContext.createRadialGradient(32, 32, 2, 32, 32, 32);
  gradient.addColorStop(0, "rgba(90,100,135,.22)");
  gradient.addColorStop(1, "rgba(90,100,135,0)");
  shadowContext.fillStyle = gradient;
  shadowContext.fillRect(0, 0, 64, 64);
  const shadowTexture = new T.CanvasTexture(shadowCanvas);
  const floor = new T.Mesh(
    new T.PlaneGeometry(2.5, 1.5),
    new T.MeshBasicMaterial({
      map: shadowTexture,
      transparent: true,
      depthWrite: false,
    }),
  );
  floor.rotation.x = -Math.PI / 2;
  floor.position.y = 0.005;
  scene.add(floor);
  // Handmade fleece detail stays subtle, with no visible grain at app icon size.
  const c = document.createElement("canvas");
  c.width = c.height = 128;
  const ctx = c.getContext("2d"),
    data = ctx.createImageData(128, 128),
    h = [];
  let seed = 11;
  for (let i = 0; i < 128 * 128; i++) {
    seed = (seed * 1664525 + 1013904223) >>> 0;
    h.push(126 + ((seed >>> 24) - 128) * 0.18);
  }
  for (let y = 0; y < 128; y++)
    for (let x = 0; x < 128; x++) {
      const dx =
          (h[y * 128 + ((x + 1) % 128)] - h[y * 128 + ((x + 127) % 128)]) / 255,
        dy =
          (h[((y + 1) % 128) * 128 + x] - h[((y + 127) % 128) * 128 + x]) / 255,
        q = Math.sqrt(16 * dx * dx + 16 * dy * dy + 1);
      data.data.set(
        [
          128 + (127 * dx * 4) / q,
          128 - (127 * dy * 4) / q,
          128 + 127 / q,
          255,
        ],
        4 * (y * 128 + x),
      );
    }
  ctx.putImageData(data, 0, 0);
  const tex = new T.CanvasTexture(c);
  tex.wrapS = tex.wrapT = T.RepeatWrapping;
  tex.repeat.set(2, 2);
  white.normalMap = tex;
  white.normalScale.set(0.16, 0.2);
  white.needsUpdate = true;
  for (const ear of ears) head.attach(ear);

  const openingMount = surface(0, -0.305, 0.024);
  const opening = mesh(
    head,
    brown,
    [openingMount.p.x, openingMount.p.y, openingMount.p.z],
    [0.055, 0.001, 0.016],
  );
  opening.quaternion.setFromUnitVectors(new T.Vector3(0, 0, 1), openingMount.n);
  opening.visible = false;
  const phases = new Set([
    "IDLE",
    "PREPARING",
    "LISTENING",
    "THINKING",
    "ANSWER_READY",
    "SPEAKING",
    "ERROR",
  ]);
  const reduced = matchMedia("(prefers-reduced-motion: reduce)");
  let blend = 0,
    nextBlink = 3.8,
    blinkStart = -1;
  function pose(dt) {
    elapsed += dt;
    const speaking = phase === "SPEAKING",
      thinking = phase === "THINKING",
      listening = phase === "LISTENING";
    const amplitude = reduced.matches ? 0 : 1;
    const target = thinking
      ? 0.06
      : listening
        ? -0.045
        : phase === "ERROR"
          ? 0.045
          : phase === "PREPARING"
            ? -0.025
            : 0;
    blend = reduced.matches
      ? target
      : T.MathUtils.lerp(blend, target, 1 - Math.exp(-dt * 9));
    head.rotation.z =
      blend + amplitude * (speaking ? Math.sin(elapsed * 4) * 0.023 : 0);
    head.rotation.y =
      amplitude *
      (thinking
        ? Math.sin(elapsed * 0.7) * 0.055
        : Math.sin(elapsed * 0.42) * 0.012);
    head.position.y =
      1.86 + amplitude * (speaking ? Math.sin(elapsed * 4) * 0.012 : 0);
    body.scale.y = 0.65 * (1 + amplitude * 0.006 * Math.sin(elapsed * 1.6));
    ears.forEach((ear, i) => {
      ear.rotation.z =
        (i ? 1 : -1) * (listening ? -0.025 : 0) +
        amplitude *
          0.025 *
          Math.sin(elapsed * (speaking ? 3 : 1.3) - 0.45 + i * 0.25);
    });
    if (elapsed >= nextBlink) {
      blinkStart = elapsed;
      nextBlink = elapsed + 3.5 + (Math.sin(elapsed * 7) + 1);
    }
    const blinkTime = elapsed - blinkStart;
    const blink =
      amplitude && blinkStart >= 0 && blinkTime < 0.15
        ? Math.sin((Math.PI * blinkTime) / 0.15)
        : 0;
    eyes.forEach((eye) => (eye.scale.y = 1 - 0.94 * blink));
    opening.visible = speaking && !reduced.matches;
    opening.scale.y = 0.012 + Math.max(0, Math.sin(elapsed * 9)) * 0.048;
  }
  function render() {
    renderer.render(scene, camera);
    api.ready = true;
  }
  function frame(now) {
    raf = 0;
    if (disposed || paused || api.failed) return;
    if (!last || now - last >= 1000 / 24) {
      const dt = last ? Math.min((now - last) / 1000, 0.1) : 1 / 24;
      last = now;
      pose(dt);
      render();
    }
    if (!reduced.matches) raf = requestAnimationFrame(frame);
  }
  function schedule() {
    if (!disposed && !paused && !api.failed && !raf)
      raf = requestAnimationFrame(frame);
  }
  api.setPhase = (name) => {
    if (!disposed && !api.failed && phases.has(name)) {
      phase = name;
      pose(1 / 24);
      if (!paused) render();
      schedule();
    }
  };
  api.setPaused = (value) => {
    if (disposed || api.failed) return;
    paused = Boolean(value);
    last = 0;
    if (paused) {
      cancelAnimationFrame(raf);
      raf = 0;
    } else schedule();
  };
  function resize() {
    if (disposed || api.failed) return;
    const w = Math.max(1, innerWidth),
      h = Math.max(1, innerHeight);
    renderer.setPixelRatio(
      Math.min(devicePixelRatio || 1, 1.5, 640 / Math.max(w, h)),
    );
    renderer.setSize(w, h);
    camera.left = (-v * w) / h / 2;
    camera.right = (v * w) / h / 2;
    camera.updateProjectionMatrix();
    if (!paused) render();
  }
  function lost(event) {
    event.preventDefault();
    api.failed = true;
    api.ready = false;
    cancelAnimationFrame(raf);
    raf = 0;
    document.documentElement.dataset.failed = "true";
    renderer.domElement.style.visibility = "hidden";
  }
  function visibility() {
    if (document.hidden) {
      api.setPaused(true);
    }
  }
  function motion() {
    last = 0;
    schedule();
  }
  addEventListener("resize", resize);
  document.addEventListener("visibilitychange", visibility);
  renderer.domElement.addEventListener("webglcontextlost", lost);
  reduced.addEventListener?.("change", motion);
  api.dispose = () => {
    if (disposed) return;
    disposed = true;
    api.ready = false;
    cancelAnimationFrame(raf);
    removeEventListener("resize", resize);
    document.removeEventListener("visibilitychange", visibility);
    reduced.removeEventListener?.("change", motion);
    renderer.domElement.removeEventListener("webglcontextlost", lost);
    const geometries = new Set(),
      materials = new Set(),
      textures = new Set();
    scene.traverse((o) => {
      if (o.geometry) geometries.add(o.geometry);
      if (o.material) materials.add(o.material);
    });
    for (const m of materials) {
      for (const value of Object.values(m))
        if (value?.isTexture) textures.add(value);
      m.dispose();
    }
    geometries.forEach((g) => g.dispose());
    textures.forEach((t) => t.dispose());
    renderer.dispose();
    renderer.forceContextLoss();
  };
  // Read-only diagnostics for browser verification; no scene or native capability exposed.
  api.diagnostics = () => ({
    phase,
    paused,
    disposed,
    ready: api.ready,
    failed: api.failed,
    frames: renderer.info.render.frame,
    triangles: renderer.info.render.triangles,
    calls: renderer.info.render.calls,
    width: renderer.domElement.width,
    height: renderer.domElement.height,
    revision: T.REVISION,
  });
  pose(0);
  render();
} catch (error) {
  api.failed = true;
  api.ready = false;
  document.documentElement.dataset.failed = "true";
  console.error("Mascot initialization failed", error);
  renderer?.dispose();
}
