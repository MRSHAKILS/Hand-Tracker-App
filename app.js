const video = document.querySelector("#camera");
const canvas = document.querySelector("#paint");
const context = canvas.getContext("2d", { alpha: true });
const stage = document.querySelector(".stage");
const startButton = document.querySelector("#startButton");
const statusText = document.querySelector("#status");

const handsReady = waitForGlobal("Hands");

let hands;
let isTracking = false;
let canvasWidth = 0;
let canvasHeight = 0;

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

function resizeCanvas() {
  const rect = canvas.getBoundingClientRect();
  const dpr = window.devicePixelRatio || 1;
  const width = Math.round(rect.width * dpr);
  const height = Math.round(rect.height * dpr);

  if (canvasWidth === width && canvasHeight === height) {
    return;
  }

  canvasWidth = width;
  canvasHeight = height;
  canvas.width = width;
  canvas.height = height;
  context.setTransform(dpr, 0, 0, dpr, 0, 0);
}

function drawFingerDot(landmarks) {
  resizeCanvas();
  context.clearRect(0, 0, canvas.clientWidth, canvas.clientHeight);

  if (!landmarks?.length) {
    return;
  }

  const fingertip = landmarks[8];
  const x = (1 - fingertip.x) * canvas.clientWidth;
  const y = fingertip.y * canvas.clientHeight;

  context.beginPath();
  context.arc(x, y, 13, 0, Math.PI * 2);
  context.fillStyle = "#ffd84d";
  context.shadowColor = "#ff5b9a";
  context.shadowBlur = 20;
  context.fill();
  context.shadowBlur = 0;
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

  hands.onResults((results) => {
    drawFingerDot(results.multiHandLandmarks?.[0]);
  });
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

window.addEventListener("resize", resizeCanvas);
startButton.addEventListener("click", startTracking);

handsReady
  .then(() => setStatus("Ready"))
  .catch(() => setStatus("Check your connection"));
