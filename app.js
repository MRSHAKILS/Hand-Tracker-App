const video = document.querySelector("#camera");
const paintCanvas = document.querySelector("#paint");
const paintCtx = paintCanvas.getContext("2d", { alpha: true });
const cursorCanvas = document.querySelector("#cursor");
const cursorCtx = cursorCanvas.getContext("2d", { alpha: true });
const paletteEl = document.querySelector("#palette");
const stage = document.querySelector(".stage");
const startButton = document.querySelector("#startButton");
const statusText = document.querySelector("#status");

const handsReady = waitForGlobal("Hands");

const CLEAR_HOLD_MS = 600;
const CLEAR_FADE_FRAMES = 14;
const SWATCH_HOLD_MS = 450;

// Brush sizing. Hand-size is measured as the wrist -> middle-MCP distance in
// MediaPipe's normalized coordinates (0..1 of the input frame). The near/far
// thresholds were picked to feel right for a typical webcam at arm's length.
const MIN_BRUSH_RADIUS = 3;
const MAX_BRUSH_RADIUS = 22;
const MIN_HAND_SIZE = 0.09;
const MAX_HAND_SIZE = 0.26;
const BRUSH_SMOOTHING = 0.18;
const SPACING_PER_RADIUS = 1.3;

const palette = [
  { color: "#ff5252", glow: "#ffb1b1" },
  { color: "#ff9c3a", glow: "#ffcf95" },
  { color: "#ffd84d", glow: "#fff39e" },
  { color: "#7ed957", glow: "#c5f0a8" },
  { color: "#13c8c0", glow: "#85ebe5" },
  { color: "#5fa8ff", glow: "#aacbff" },
  { color: "#b266ff", glow: "#d8b3ff" },
  { color: "#ff5b9a", glow: "#ffacc9" },
  { color: "#fff8d8", glow: "#ffffff" }
];

let activeColorIdx = 2;
let hoveredSwatchIdx = null;
const swatchHold = { startedAt: null };
const swatchEls = [];
let swatchRects = [];

const brush = {
  smoothedSize: null,
  radius: (MIN_BRUSH_RADIUS + MAX_BRUSH_RADIUS) / 2,
  spacing: ((MIN_BRUSH_RADIUS + MAX_BRUSH_RADIUS) / 2) * SPACING_PER_RADIUS
};

const surfaces = [
  { canvas: paintCanvas, ctx: paintCtx, persistent: true, lastW: 0, lastH: 0 },
  { canvas: cursorCanvas, ctx: cursorCtx, persistent: false, lastW: 0, lastH: 0 }
];

const pen = {
  active: false,
  prevPoint: null,
  prevMidpoint: null,
  distanceLeft: 0
};

const clearHold = {
  startedAt: null,
  animating: false
};

let hands;
let isTracking = false;

function waitForGlobal(name) {
  return new Promise((resolve, reject) => {
    const startedAt = performance.now();

    function check() {
      if (window[name]) {
        resolve(window[name]);
        return;
      }

      if (performance.now() - startedAt > 15000) {
        reject(new Error(`${name} did not load`));
        return;
      }

      requestAnimationFrame(check);
    }

    check();
  });
}

function setStatus(message) {
  statusText.textContent = message;
}

function resizeSurfaces() {
  const dpr = window.devicePixelRatio || 1;

  for (const surface of surfaces) {
    const rect = surface.canvas.getBoundingClientRect();
    const width = Math.round(rect.width * dpr);
    const height = Math.round(rect.height * dpr);

    if (surface.lastW === width && surface.lastH === height) {
      continue;
    }

    if (surface.persistent && surface.lastW > 0 && surface.lastH > 0) {
      // Preserve existing trail across resizes by snapshotting and restretching.
      const snapshot = document.createElement("canvas");
      snapshot.width = surface.canvas.width;
      snapshot.height = surface.canvas.height;
      snapshot.getContext("2d").drawImage(surface.canvas, 0, 0);

      surface.canvas.width = width;
      surface.canvas.height = height;
      surface.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      surface.ctx.drawImage(snapshot, 0, 0, width / dpr, height / dpr);
    } else {
      surface.canvas.width = width;
      surface.canvas.height = height;
      surface.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }

    surface.lastW = width;
    surface.lastH = height;
  }
}

function landmarkToCanvas(landmark) {
  // Video is mirrored via CSS scaleX(-1), so mirror x to match what the user sees.
  return {
    x: (1 - landmark.x) * paintCanvas.clientWidth,
    y: landmark.y * paintCanvas.clientHeight
  };
}

function isFingerExtended(landmarks, tipIdx, pipIdx) {
  return landmarks[tipIdx].y < landmarks[pipIdx].y;
}

function classifyGesture(landmarks) {
  return {
    indexUp: isFingerExtended(landmarks, 8, 6),
    middleUp: isFingerExtended(landmarks, 12, 10),
    ringUp: isFingerExtended(landmarks, 16, 14),
    pinkyUp: isFingerExtended(landmarks, 20, 18)
  };
}

function handSize(landmarks) {
  // Distance from wrist (0) to middle MCP (9) in MediaPipe normalized coords.
  // This palm-spine length is stable across finger poses, unlike fingertip
  // distances that change with which fingers are extended.
  const wrist = landmarks[0];
  const midMcp = landmarks[9];
  return Math.hypot(wrist.x - midMcp.x, wrist.y - midMcp.y);
}

function updateBrush(landmarks) {
  const raw = handSize(landmarks);
  brush.smoothedSize =
    brush.smoothedSize === null
      ? raw
      : brush.smoothedSize * (1 - BRUSH_SMOOTHING) + raw * BRUSH_SMOOTHING;

  const range = MAX_HAND_SIZE - MIN_HAND_SIZE;
  const t = Math.max(0, Math.min(1, (brush.smoothedSize - MIN_HAND_SIZE) / range));
  brush.radius = MIN_BRUSH_RADIUS + (MAX_BRUSH_RADIUS - MIN_BRUSH_RADIUS) * t;
  brush.spacing = Math.max(2, brush.radius * SPACING_PER_RADIUS);
}

function drawDot(x, y) {
  const swatch = palette[activeColorIdx];
  paintCtx.beginPath();
  paintCtx.arc(x, y, brush.radius, 0, Math.PI * 2);
  paintCtx.fillStyle = swatch.color;
  paintCtx.shadowColor = swatch.glow;
  paintCtx.shadowBlur = Math.max(8, brush.radius * 2);
  paintCtx.fill();
  paintCtx.shadowBlur = 0;
}

function dropDotsAlongCurve(p0, p1, p2) {
  // Sample a quadratic Bezier: P0 (start) -> P1 (control) -> P2 (end).
  // Walking the curve in small steps lets us drop dots at constant arc-length.
  const approxLength =
    Math.hypot(p1.x - p0.x, p1.y - p0.y) + Math.hypot(p2.x - p1.x, p2.y - p1.y);
  const samples = Math.max(2, Math.ceil(approxLength / 2));

  let prevX = p0.x;
  let prevY = p0.y;

  for (let i = 1; i <= samples; i++) {
    const t = i / samples;
    const mt = 1 - t;
    const x = mt * mt * p0.x + 2 * mt * t * p1.x + t * t * p2.x;
    const y = mt * mt * p0.y + 2 * mt * t * p1.y + t * t * p2.y;

    pen.distanceLeft += Math.hypot(x - prevX, y - prevY);
    if (pen.distanceLeft >= brush.spacing) {
      drawDot(x, y);
      pen.distanceLeft = 0;
    }

    prevX = x;
    prevY = y;
  }
}

function drawCursor(x, y, mode, clearProgress = 0) {
  cursorCtx.clearRect(0, 0, cursorCanvas.clientWidth, cursorCanvas.clientHeight);

  if (mode === "draw") {
    const swatch = palette[activeColorIdx];
    cursorCtx.beginPath();
    cursorCtx.arc(x, y, brush.radius + 4, 0, Math.PI * 2);
    cursorCtx.fillStyle = swatch.color;
    cursorCtx.globalAlpha = 0.85;
    cursorCtx.shadowColor = swatch.glow;
    cursorCtx.shadowBlur = Math.max(14, brush.radius * 2.2);
    cursorCtx.fill();
    cursorCtx.globalAlpha = 1;
    cursorCtx.shadowBlur = 0;
    return;
  }

  if (mode === "swatch") {
    cursorCtx.beginPath();
    cursorCtx.arc(x, y, 4, 0, Math.PI * 2);
    cursorCtx.fillStyle = "#fff8d8";
    cursorCtx.shadowColor = "rgba(255, 248, 216, 0.9)";
    cursorCtx.shadowBlur = 10;
    cursorCtx.fill();
    cursorCtx.shadowBlur = 0;
    return;
  }

  if (mode === "clear") {
    // Soft pink halo plus a charging progress arc.
    cursorCtx.beginPath();
    cursorCtx.arc(x, y, 22, 0, Math.PI * 2);
    cursorCtx.fillStyle = "rgba(255, 91, 154, 0.18)";
    cursorCtx.fill();

    cursorCtx.beginPath();
    cursorCtx.arc(x, y, 20, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * clearProgress);
    cursorCtx.strokeStyle = "#ff5b9a";
    cursorCtx.lineWidth = 4;
    cursorCtx.lineCap = "round";
    cursorCtx.stroke();

    cursorCtx.beginPath();
    cursorCtx.arc(x, y, 4, 0, Math.PI * 2);
    cursorCtx.fillStyle = "#fff8d8";
    cursorCtx.fill();
    return;
  }

  // Hover cursor: a hollow ring sized to preview the current brush, plus a
  // small inner dot so the fingertip position is still pinpointed.
  const hoverRadius = Math.max(10, brush.radius + 4);
  cursorCtx.beginPath();
  cursorCtx.arc(x, y, hoverRadius, 0, Math.PI * 2);
  cursorCtx.strokeStyle = "rgba(19, 200, 192, 0.95)";
  cursorCtx.lineWidth = 3;
  cursorCtx.stroke();

  cursorCtx.beginPath();
  cursorCtx.arc(x, y, 3, 0, Math.PI * 2);
  cursorCtx.fillStyle = "rgba(19, 200, 192, 0.95)";
  cursorCtx.fill();
}

function clearTrail() {
  // Fades the persistent trail to nothing over a handful of frames using
  // destination-out compositing, then clears any residue. Feels like a soft
  // "puff" rather than a jarring instant wipe.
  if (clearHold.animating) {
    return;
  }
  clearHold.animating = true;

  let frame = 0;
  const step = () => {
    paintCtx.save();
    paintCtx.globalCompositeOperation = "destination-out";
    paintCtx.fillStyle = "rgba(0, 0, 0, 0.35)";
    paintCtx.fillRect(0, 0, paintCanvas.clientWidth, paintCanvas.clientHeight);
    paintCtx.restore();

    frame += 1;
    if (frame < CLEAR_FADE_FRAMES) {
      requestAnimationFrame(step);
    } else {
      paintCtx.clearRect(0, 0, paintCanvas.clientWidth, paintCanvas.clientHeight);
      clearHold.animating = false;
    }
  };
  requestAnimationFrame(step);
}

function liftPen() {
  pen.active = false;
  pen.prevPoint = null;
  pen.prevMidpoint = null;
  pen.distanceLeft = 0;
}

function clearCursor() {
  cursorCtx.clearRect(0, 0, cursorCanvas.clientWidth, cursorCanvas.clientHeight);
}

function createPalette() {
  paletteEl.innerHTML = "";
  swatchEls.length = 0;

  palette.forEach((swatch, index) => {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "swatch";
    btn.dataset.index = String(index);
    btn.style.setProperty("--swatch-color", swatch.color);
    btn.style.setProperty("--swatch-glow", swatch.glow);
    btn.setAttribute("aria-label", `Color ${index + 1}`);
    btn.addEventListener("click", () => setActiveColor(index));
    paletteEl.appendChild(btn);
    swatchEls.push(btn);
  });

  updateActiveSwatchUi();
  requestAnimationFrame(updateSwatchRects);
}

function setActiveColor(index) {
  if (index === activeColorIdx) {
    return;
  }
  activeColorIdx = index;
  updateActiveSwatchUi();
}

function updateActiveSwatchUi() {
  swatchEls.forEach((el, i) => {
    el.classList.toggle("is-active", i === activeColorIdx);
  });
}

function updateSwatchRects() {
  const stageRect = stage.getBoundingClientRect();
  swatchRects = swatchEls.map((el, i) => {
    const r = el.getBoundingClientRect();
    return {
      index: i,
      left: r.left - stageRect.left,
      top: r.top - stageRect.top,
      right: r.right - stageRect.left,
      bottom: r.bottom - stageRect.top
    };
  });
}

function findSwatchAt(x, y) {
  for (const r of swatchRects) {
    if (x >= r.left && x <= r.right && y >= r.top && y <= r.bottom) {
      return r.index;
    }
  }
  return null;
}

function setSwatchProgress(index, progress) {
  if (index === null || index === undefined) {
    return;
  }
  const el = swatchEls[index];
  if (!el) {
    return;
  }
  const clamped = Math.max(0, Math.min(1, progress));
  el.style.setProperty("--swatch-progress", `${clamped * 100}%`);
}

function resetSwatchHold() {
  if (hoveredSwatchIdx !== null) {
    setSwatchProgress(hoveredSwatchIdx, 0);
  }
  hoveredSwatchIdx = null;
  swatchHold.startedAt = null;
}

function processFrame(results) {
  resizeSurfaces();

  const landmarks = results.multiHandLandmarks?.[0];
  if (!landmarks?.length) {
    liftPen();
    clearHold.startedAt = null;
    resetSwatchHold();
    clearCursor();
    return;
  }

  updateBrush(landmarks);

  const fingertip = landmarkToCanvas(landmarks[8]);
  const gesture = classifyGesture(landmarks);
  const isClearGesture =
    gesture.indexUp && gesture.middleUp && gesture.ringUp && gesture.pinkyUp;
  const shouldDraw =
    gesture.indexUp && !gesture.middleUp && !isClearGesture;

  if (isClearGesture) {
    liftPen();
    resetSwatchHold();
    if (clearHold.startedAt === null) {
      clearHold.startedAt = performance.now();
    }
    const elapsed = performance.now() - clearHold.startedAt;
    const progress = Math.min(1, elapsed / CLEAR_HOLD_MS);
    drawCursor(fingertip.x, fingertip.y, "clear", progress);

    if (progress >= 1) {
      clearTrail();
      clearHold.startedAt = null;
    }
    return;
  }

  clearHold.startedAt = null;

  if (shouldDraw) {
    resetSwatchHold();
    drawCursor(fingertip.x, fingertip.y, "draw");
  } else {
    // Hover mode: pen lifted. This is the only state where swatch picking happens.
    liftPen();
    const swatchIdx = findSwatchAt(fingertip.x, fingertip.y);

    if (swatchIdx === null || swatchIdx === activeColorIdx) {
      resetSwatchHold();
      drawCursor(fingertip.x, fingertip.y, swatchIdx === null ? "hover" : "swatch");
      return;
    }

    if (hoveredSwatchIdx !== swatchIdx) {
      if (hoveredSwatchIdx !== null) {
        setSwatchProgress(hoveredSwatchIdx, 0);
      }
      hoveredSwatchIdx = swatchIdx;
      swatchHold.startedAt = performance.now();
    }

    const elapsed = performance.now() - swatchHold.startedAt;
    const progress = Math.min(1, elapsed / SWATCH_HOLD_MS);
    setSwatchProgress(swatchIdx, progress);
    drawCursor(fingertip.x, fingertip.y, "swatch");

    if (progress >= 1) {
      setActiveColor(swatchIdx);
      resetSwatchHold();
    }
    return;
  }

  if (!pen.active) {
    // Start of a new stroke: anchor history at the current fingertip and seed a dot.
    pen.active = true;
    pen.prevPoint = fingertip;
    pen.prevMidpoint = fingertip;
    pen.distanceLeft = 0;
    drawDot(fingertip.x, fingertip.y);
    return;
  }

  // Bezier-smooth the path: use the fingertip as the control point and the
  // midpoints of consecutive samples as the curve's start and end. This avoids
  // sharp corners at every raw sample and gives the trail a fluid feel.
  const newMidpoint = {
    x: (pen.prevPoint.x + fingertip.x) / 2,
    y: (pen.prevPoint.y + fingertip.y) / 2
  };

  dropDotsAlongCurve(pen.prevMidpoint, pen.prevPoint, newMidpoint);

  pen.prevMidpoint = newMidpoint;
  pen.prevPoint = fingertip;
}

async function createTracker() {
  const Hands = await handsReady;

  hands = new Hands({
    locateFile: (file) => `./node_modules/@mediapipe/hands/${file}`
  });

  hands.setOptions({
    maxNumHands: 1,
    modelComplexity: 1,
    minDetectionConfidence: 0.7,
    minTrackingConfidence: 0.65
  });

  hands.onResults(processFrame);
}

async function startCamera() {
  if (!navigator.mediaDevices?.getUserMedia) {
    throw new Error("This browser does not support webcam access");
  }

  const stream = await navigator.mediaDevices.getUserMedia({
    audio: false,
    video: {
      facingMode: "user",
      width: { ideal: 1280 },
      height: { ideal: 720 }
    }
  });

  video.srcObject = stream;
  await video.play();
}

function stopCamera() {
  const stream = video.srcObject;

  if (!stream) {
    return;
  }

  for (const track of stream.getTracks()) {
    track.stop();
  }

  video.srcObject = null;
}

async function trackFrame() {
  if (!isTracking) {
    return;
  }

  if (video.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA) {
    try {
      await hands.send({ image: video });
    } catch (error) {
      console.error(error);
      isTracking = false;
      stage.classList.remove("is-live");
      setStatus("Tracker paused.");
      return;
    }
  }

  requestAnimationFrame(trackFrame);
}

async function startTracking() {
  startButton.disabled = true;
  setStatus("Camera permission...");

  try {
    await startCamera();
    stage.classList.add("is-live");
    setStatus("Loading tracker...");

    await createTracker();
    isTracking = true;
    requestAnimationFrame(trackFrame);
  } catch (error) {
    console.error(error);
    isTracking = false;
    stopCamera();
    stage.classList.remove("is-live");
    startButton.disabled = false;
    setStatus(error.name === "NotAllowedError" ? "Camera needs permission." : "Tracker could not start.");
  }
}

window.addEventListener("resize", () => {
  resizeSurfaces();
  updateSwatchRects();
});
startButton.addEventListener("click", startTracking);

createPalette();

handsReady
  .then(() => setStatus("Ready"))
  .catch(() => setStatus("Check your connection"));
