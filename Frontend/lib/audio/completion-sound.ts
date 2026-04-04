const COMPLETION_SOUND_PATH = "/audio/resumade-human-patch-complete.mp3";
const MUTED_KEY = "resumade:sound-muted";

let audioContext: AudioContext | null = null;
let audioBufferPromise: Promise<AudioBuffer | null> | null = null;
let fallbackAudio: HTMLAudioElement | null = null;
let completionSoundUnlocked = false;

export function isSoundMuted(): boolean {
  if (typeof window === "undefined") return false;
  return localStorage.getItem(MUTED_KEY) === "true";
}

export function setSoundMuted(muted: boolean): void {
  if (typeof window === "undefined") return;
  localStorage.setItem(MUTED_KEY, String(muted));
  window.dispatchEvent(new CustomEvent("resumade:sound-muted-change", { detail: { muted } }));
}

function getAudioContext() {
  if (typeof window === "undefined") {
    return null;
  }

  const AudioContextCtor =
    window.AudioContext ||
    (window as typeof window & { webkitAudioContext?: typeof AudioContext })
      .webkitAudioContext;

  if (!AudioContextCtor) {
    return null;
  }

  if (!audioContext) {
    audioContext = new AudioContextCtor();
  }

  return audioContext;
}

function getFallbackAudio() {
  if (typeof window === "undefined") {
    return null;
  }

  if (!fallbackAudio) {
    fallbackAudio = new Audio(COMPLETION_SOUND_PATH);
    fallbackAudio.preload = "auto";
    fallbackAudio.volume = 1;
  }

  return fallbackAudio;
}

async function loadAudioBuffer(context: AudioContext) {
  if (!audioBufferPromise) {
    audioBufferPromise = fetch(COMPLETION_SOUND_PATH)
      .then((response) => {
        if (!response.ok) {
          throw new Error(`Failed to load completion sound: ${response.status}`);
        }
        return response.arrayBuffer();
      })
      .then((buffer) => context.decodeAudioData(buffer.slice(0)))
      .catch((error) => {
        console.error("Failed to decode completion sound", error);
        audioBufferPromise = null;
        return null;
      });
  }

  return audioBufferPromise;
}

export async function prepareCompletionSound() {
  const context = getAudioContext();

  if (context) {
    try {
      if (context.state === "suspended") {
        await context.resume();
      }
      completionSoundUnlocked = true;
      void loadAudioBuffer(context);
      return;
    } catch (error) {
      console.error("Failed to unlock completion sound audio context", error);
    }
  }

  const audio = getFallbackAudio();
  if (!audio) {
    return;
  }

  audio.load();

  if (completionSoundUnlocked) {
    return;
  }

  const previousMuted = audio.muted;
  const previousVolume = audio.volume;
  audio.muted = true;
  audio.volume = 0;
  audio.currentTime = 0;

  try {
    await audio.play();
    audio.pause();
    audio.currentTime = 0;
    completionSoundUnlocked = true;
  } catch {
    // Ignore unlock failures. Playback may still succeed later.
  } finally {
    audio.muted = previousMuted;
    audio.volume = previousVolume;
  }
}

export async function playCompletionSound() {
  if (isSoundMuted()) return;
  const context = getAudioContext();

  if (context) {
    try {
      if (context.state === "suspended") {
        await context.resume();
      }

      const buffer = await loadAudioBuffer(context);
      if (buffer) {
        const source = context.createBufferSource();
        source.buffer = buffer;
        source.connect(context.destination);
        source.start(0);
        return;
      }
    } catch (error) {
      console.error("Failed to play completion sound with AudioContext", error);
    }
  }

  const audio = getFallbackAudio();
  if (!audio) {
    return;
  }

  audio.pause();
  audio.currentTime = 0;
  audio.muted = false;
  audio.volume = 1;

  try {
    await audio.play();
  } catch {
    // If autoplay is blocked, there is nothing else to do here.
  }
}
