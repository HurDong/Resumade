import { useState, useEffect, useCallback } from "react"
import { fetchCalendarEvents, toCalendarEvent } from "@/lib/api/calendar"
import {
  mockCalendarEvents,
  type CalendarEvent,
} from "@/components/recruitment-calendar/calendar-mock-data"
import { isFrontendOnlyMode } from "@/lib/frontend-only"

interface UseCalendarEventsResult {
  data: CalendarEvent[]
  isLoading: boolean
  isError: boolean
  refetch: () => void
}

export function useCalendarEvents(year: number, month: number): UseCalendarEventsResult {
  const [data, setData] = useState<CalendarEvent[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isError, setIsError] = useState(false)

  const load = useCallback(async () => {
    setIsLoading(true)
    setIsError(false)
    if (isFrontendOnlyMode()) {
      setData(mockCalendarEvents)
      setIsLoading(false)
      return
    }

    try {
      const dtos = await fetchCalendarEvents(year, month)
      setData(dtos.map(toCalendarEvent))
    } catch {
      setData(mockCalendarEvents)
      setIsError(false)
    } finally {
      setIsLoading(false)
    }
  }, [year, month])

  useEffect(() => {
    load()
  }, [load])

  return { data, isLoading, isError, refetch: load }
}
