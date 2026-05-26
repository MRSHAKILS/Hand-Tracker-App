const video = document.querySelector("#camera");
const paintCanvas = document.querySelector("#paint");
const paintCtx = paintCanvas.getContext("2d", { alpha: true });
const cursorCanvas = document.querySelector("#cursor");
const cursorCtx = cursorCanvas.getContext("2d", { alpha: true });
const stage = document.querySelector(".stage");
const startButton = document.querySelector("#startButton");
const statusText = document.querySelector("#status");

const handsReady = waitForGlobal("Hands");

const DOT_SPACING = 8;
const DOT_RADIUS = 5;
const DOT_COLOR = "#ffd84d";
const DOT_GLOW = "#ff5b9a";
const CLEAR_HOLD_MS = 600;
const CLEAR_FADE_FRAMES = 14;

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

function drawDot(x, y) {
  paintCtx.beginPath();
  paintCtx.arc(x, y, DOT_RADIUS, 0, Math.PI * 2);
  paintCtx.fillStyle = DOT_COLOR;
  paintCtx.shadowColor = DOT_GLOW;
  paintCtx.shadowBlur = 14;
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
    if (pen.distanceLeft >= DOT_SPACING) {
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
    cursorCtx.beginPath();
    cursorCtx.arc(x, y, DOT_RADIUS + 5, 0, Math.PI * 2);
    cursorCtx.fillStyle = "rgba(255, 216, 77, 0.85)";
    cursorCtx.shadowColor = DOT_GLOW;
    cursorCtx.shadowBlur = 20;
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

  cursorCtx.beginPath();
  cursorCtx.arc(x, y, 14, 0, Math.PI * 2);
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

function processFrame(results) {
  resizeSurfaces();

  const landmarks = results.multiHandLandmarks?.[0];
  if (!landmarks?.length) {
    liftPen();
    clearHold.startedAt = null;
    clearCursor();
    return;
  }

  const fingertip = landmarkToCanvas(landmarks[8]);
  const gesture = classifyGesture(landmarks);
  const isClearGesture =
    gesture.indexUp && gesture.middleUp && gesture.ringUp && gesture.pinkyUp;
  const shouldDraw =
    gesture.indexUp && !gesture.middleUp && !isClearGesture;

  if (isClearGesture) {
    liftPen();
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
  drawCursor(fingertip.x, fingertip.y, shouldDraw ? "draw" : "hover");

  if (!shouldDraw) {
    liftPen();
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

window.addEventListener("resize", resizeSurfaces);
startButton.addEventListener("click", startTracking);

handsReady
  .then(() => setStatus("Ready"))
  .catch(() => setStatus("Check your connection"));
