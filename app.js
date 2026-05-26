const video = document.querySelector("#camera");
const canvas = document.querySelector("#paint");
const context = canvas.getContext("2d", { alpha: true });
const stage = document.querySelector(".stage");
const startButton = document.querySelector("#startButton");
const statusText = document.querySelector("#status");

const handsReady = waitForGlobal("Hands");
const cameraUtilsReady = waitForGlobal("Camera");
const opencvReady = waitForOpenCv();

let hands;
let camera;
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

function waitForOpenCv() {
  return new Promise((resolve, reject) => {
    if (window.cv?.Mat) {
      resolve(window.cv);
      return;
    }

    const timeout = window.setTimeout(() => {
      reject(new Error("OpenCV did not load"));
    }, 20000);

    window.addEventListener(
      "opencv-ready",
      () => {
        window.clearTimeout(timeout);
        resolve(window.cv);
      },
      { once: true }
    );
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
  const [Hands] = await Promise.all([handsReady, cameraUtilsReady, opencvReady]);

  hands = new Hands({
    locateFile: (file) => `https://cdn.jsdelivr.net/npm/@mediapipe/hands/${file}`
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

async function startTracking() {
  startButton.disabled = true;
  setStatus("Starting...");

  try {
    await createTracker();
    const Camera = await cameraUtilsReady;

    camera = new Camera(video, {
      width: 1280,
      height: 720,
      onFrame: async () => {
        await hands.send({ image: video });
      }
    });

    await camera.start();
    stage.classList.add("is-live");
  } catch (error) {
    console.error(error);
    startButton.disabled = false;
    setStatus("Camera needs permission.");
  }
}

window.addEventListener("resize", resizeCanvas);
startButton.addEventListener("click", startTracking);

Promise.all([handsReady, cameraUtilsReady, opencvReady])
  .then(() => setStatus("Ready"))
  .catch(() => setStatus("Check your connection"));
