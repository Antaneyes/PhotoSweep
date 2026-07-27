(() => {
  "use strict";

  const NATIVE_APP = "photosweep";
  const port = browser.runtime.connectNative(NATIVE_APP);
  let scanning = false;
  let thumbnailQueue = Promise.resolve();

  const send = (type, payload = {}) => port.postMessage({ type, ...payload });
  const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));

  function pageWindow() {
    return window.wrappedJSObject || window;
  }

  function globals() {
    const data = pageWindow().WIZ_global_data;
    if (!data) throw new Error("Google Photos todavía no ha inicializado la sesión");
    return {
      rapt: data.Dbw5Ud,
      sid: data.FdrFJe,
      build: data.cfb2h,
      path: data.eptZe,
      at: data.SNlM0e
    };
  }

  async function rpc(rpcId, requestData) {
    const g = globals();
    const wrapped = [[[rpcId, JSON.stringify(requestData), null, "generic"]]];
    const body = `f.req=${encodeURIComponent(JSON.stringify(wrapped))}&at=${encodeURIComponent(g.at)}&`;
    const params = new URLSearchParams({
      rpcids: rpcId,
      "source-path": location.pathname,
      "f.sid": g.sid,
      bl: g.build,
      pageId: "none",
      rt: "c"
    });
    if (typeof g.rapt === "string") params.set("rapt", g.rapt);
    const endpoint = `https://photos.google.com${g.path}data/batchexecute?${params}`;

    let lastError;
    for (let attempt = 1; attempt <= 3; attempt += 1) {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 15000);
      try {
        const response = await fetch(endpoint, {
          method: "POST",
          credentials: "include",
          signal: controller.signal,
          headers: { "content-type": "application/x-www-form-urlencoded;charset=UTF-8" },
          body
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const text = await response.text();
        const envelope = text.split("\n").find(line => line.includes("wrb.fr"));
        if (!envelope) throw new Error("Respuesta interna sin envoltorio wrb.fr");
        const parsed = JSON.parse(envelope);
        if (!parsed?.[0]?.[2]) throw new Error("Respuesta interna incompleta");
        clearTimeout(timeout);
        return JSON.parse(parsed[0][2]);
      } catch (error) {
        clearTimeout(timeout);
        lastError = error;
        if (attempt < 3) await sleep(700 * attempt);
      }
    }
    throw lastError || new Error("La llamada interna ha fallado");
  }

  function parseItem(raw) {
    if (!raw?.[0] || !raw?.[3]) return null;
    const extensions = raw.at(-1) || {};
    return {
      mediaKey: raw[0],
      dedupKey: raw[3],
      thumbnailUrl: raw?.[1]?.[0] || "",
      width: Number(raw?.[1]?.[1] || 0),
      height: Number(raw?.[1]?.[2] || 0),
      timestamp: Number(raw?.[2] || raw?.[5] || 0),
      durationMs: Number(extensions?.[76647426]?.[0] || 0),
      isArchived: Boolean(raw?.[13]),
      isOwned: !(raw?.[7] || []).some(entry => Array.isArray(entry) && entry.includes(27))
    };
  }

  async function scanLibrary() {
    if (scanning) return;
    scanning = true;
    let nextPage = null;
    let count = 0;
    try {
      do {
        const data = await rpc("EzkLib", ["", [[4, "ra", 0, 0]], nextPage]);
        const items = (data?.[0] || []).map(parseItem).filter(Boolean);
        nextPage = data?.[1] || null;
        count += items.length;
        send("scanPage", { items, count, hasMore: Boolean(nextPage) });
        await sleep(180);
      } while (nextPage && scanning);
      send("scanComplete", { count });
    } catch (error) {
      send("bridgeError", { operation: "scan", message: String(error?.message || error) });
    } finally {
      scanning = false;
    }
  }

  async function preview(mediaKey) {
    try {
      const data = await rpc("VrseUb", [mediaKey, null, null, null, null]);
      send("preview", {
        mediaKey,
        streamUrl: data?.[1] || "",
        originalUrl: data?.[7] || ""
      });
    } catch (error) {
      send("bridgeError", { operation: "preview", message: String(error?.message || error) });
    }
  }

  async function thumbnail(mediaKey, sourceUrl) {
    const url = sourceUrl.includes("=") ? sourceUrl : `${sourceUrl}=w900-h1400-no`;
    let lastError;
    for (let attempt = 1; attempt <= 3; attempt += 1) {
      try {
        const response = await fetch(url, { credentials: "include" });
        if (!response.ok) throw new Error(`Miniatura HTTP ${response.status}`);
        const blob = await response.blob();
        const dataUrl = await new Promise((resolve, reject) => {
          const reader = new FileReader();
          reader.onload = () => resolve(String(reader.result || ""));
          reader.onerror = () => reject(reader.error || new Error("No se pudo leer la miniatura"));
          reader.readAsDataURL(blob);
        });
        send("thumbnail", { mediaKey, dataUrl });
        return;
      } catch (error) {
        lastError = error;
        if (attempt < 3) await sleep(500 * attempt);
      }
    }
    send("bridgeError", {
      operation: "thumbnail",
      message: String(lastError?.message || lastError)
    });
  }

  async function moveToTrash(requestId, keys) {
    const succeeded = [];
    const failed = [];
    for (let index = 0; index < keys.length; index += 25) {
      const chunk = keys.slice(index, index + 25);
      try {
        await rpc("XwAOJf", [null, 1, chunk.map(item => item.dedupKey), 3]);
        succeeded.push(...chunk.map(item => item.mediaKey));
      } catch (error) {
        failed.push(...chunk.map(item => item.mediaKey));
        send("bridgeError", {
          operation: "trash",
          message: `Falló un lote de ${chunk.length}: ${String(error?.message || error)}`
        });
      }
      send("trashProgress", { requestId, done: succeeded.length + failed.length, total: keys.length });
      await sleep(350);
    }
    send("trashResult", { requestId, succeeded, failed });
  }

  async function verifyAbsent(keys) {
    const wanted = new Set(keys);
    const present = new Set();
    let nextPage = null;
    let scanned = 0;
    try {
      do {
        const data = await rpc("EzkLib", ["", [[4, "ra", 0, 0]], nextPage]);
        const items = (data?.[0] || []).map(parseItem).filter(Boolean);
        items.forEach(item => {
          if (wanted.has(item.mediaKey)) present.add(item.mediaKey);
        });
        scanned += items.length;
        nextPage = data?.[1] || null;
      } while (nextPage && present.size < wanted.size);
      send("verifyAbsentResult", {
        checked: keys.length,
        scanned,
        present: Array.from(present)
      });
    } catch (error) {
      send("bridgeError", {
        operation: "verify",
        message: String(error?.message || error)
      });
    }
  }

  port.onMessage.addListener(message => {
    if (!message || typeof message.type !== "string") return;
    if (message.type === "scan") scanLibrary();
    if (message.type === "stopScan") scanning = false;
    if (message.type === "preview") preview(String(message.mediaKey || ""));
    if (message.type === "thumbnail") {
      thumbnailQueue = thumbnailQueue.then(
        () => thumbnail(String(message.mediaKey || ""), String(message.url || ""))
      );
    }
    if (message.type === "trash") moveToTrash(String(message.requestId || ""), message.items || []);
    if (message.type === "verifyAbsent") verifyAbsent(message.keys || []);
  });

  try {
    globals();
    send("ready", { path: location.pathname });
  } catch (error) {
    send("pageLoaded", { path: location.pathname, message: String(error?.message || error) });
  }
})();
