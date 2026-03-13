"use client"

import { ContextPanel } from "./context-panel"
import { TranslationPanel } from "./translation-panel"

export function WorkspaceEditor() {
  return (
    <div className="grid h-full grid-cols-2">
      <ContextPanel />
      <TranslationPanel />
    </div>
  )
}
