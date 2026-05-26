import { createReadStream, existsSync } from "node:fs";
import { extname, join, normalize, resolve } from "node:path";
import { createServer } from "node:http";

const root = resolve(".");
const port = Number.parseInt(process.env.PORT || "3000", 10);
const host = process.env.HOST || "127.0.0.1";

const types = {
  ".css": "text/css; charset=utf-8",
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml"
};

const server = createServer((request, response) => {
  const requestedUrl = new URL(request.url || "/", `http://${host}:${port}`);
  const pathname = requestedUrl.pathname === "/" ? "/index.html" : requestedUrl.pathname;
  const filepath = normalize(join(root, decodeURIComponent(pathname)));

  if (!filepath.startsWith(root) || !existsSync(filepath)) {
    response.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
    response.end("Not found");
    return;
  }

  response.writeHead(200, {
    "Content-Type": types[extname(filepath)] || "application/octet-stream"
  });
  createReadStream(filepath).pipe(response);
});

server.listen(port, host, () => {
  console.log(`Hand Tracker is running at http://${host}:${port}`);
});
