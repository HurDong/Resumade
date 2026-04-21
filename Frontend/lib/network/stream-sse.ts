export interface SseEventMessage {
  event: string;
  data: string;
  id?: string;
}

interface StreamSseOptions {
  url: string;
  method?: "GET" | "POST";
  headers?: HeadersInit;
  body?: BodyInit | null;
  signal?: AbortSignal;
  onOpen?: (response: Response) => void;
  onEvent: (message: SseEventMessage) => void;
}

export async function streamSse({
  url,
  method = "GET",
  headers,
  body,
  signal,
  onOpen,
  onEvent,
}: StreamSseOptions) {
  const response = await fetch(url, {
    method,
    headers: {
      Accept: "text/event-stream",
      "Cache-Control": "no-cache",
      ...headers,
    },
    body,
    cache: "no-store",
    signal,
  });

  if (!response.ok || !response.body) {
    const errorBody = await response.text().catch(() => "");
    throw new Error(errorBody || `SSE request failed with status ${response.status}`);
  }

  onOpen?.(response);

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done });

    let boundaryIndex = findEventBoundary(buffer);
    while (boundaryIndex >= 0) {
      const rawEvent = buffer.slice(0, boundaryIndex);
      buffer = buffer.slice(boundaryIndex).replace(/^\r?\n\r?\n/, "");

      const parsedEvent = parseSseEvent(rawEvent);
      if (parsedEvent) {
        onEvent(parsedEvent);
      }

      boundaryIndex = findEventBoundary(buffer);
    }

    if (done) {
      break;
    }
  }

  const trailingEvent = parseSseEvent(buffer);
  if (trailingEvent) {
    onEvent(trailingEvent);
  }
}

function findEventBoundary(buffer: string) {
  const crlfBoundary = buffer.indexOf("\r\n\r\n");
  const lfBoundary = buffer.indexOf("\n\n");

  if (crlfBoundary === -1) {
    return lfBoundary;
  }

  if (lfBoundary === -1) {
    return crlfBoundary;
  }

  return Math.min(crlfBoundary, lfBoundary);
}

function parseSseEvent(rawEvent: string): SseEventMessage | null {
  if (!rawEvent.trim()) {
    return null;
  }

  const lines = rawEvent.split(/\r?\n/);
  let event = "message";
  let id: string | undefined;
  const dataLines: string[] = [];

  for (const line of lines) {
    if (!line || line.startsWith(":")) {
      continue;
    }

    const separatorIndex = line.indexOf(":");
    const field = separatorIndex >= 0 ? line.slice(0, separatorIndex) : line;
    const rawValue = separatorIndex >= 0 ? line.slice(separatorIndex + 1) : "";
    const value = rawValue.startsWith(" ") ? rawValue.slice(1) : rawValue;

    if (field === "event") {
      event = value || "message";
      continue;
    }

    if (field === "data") {
      dataLines.push(value);
      continue;
    }

    if (field === "id") {
      id = value;
    }
  }

  if (dataLines.length === 0 && !id) {
    return null;
  }

  return {
    event,
    data: dataLines.join("\n"),
    id,
  };
}
