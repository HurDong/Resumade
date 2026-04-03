import { useState, useEffect, useCallback } from "react"
import { fetchCalendarEvents, toCalendarEvent } from "@/lib/api/calendar"
import type { CalendarEvent } from "@/components/recruitment-calendar/calendar-mock-data"

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
    try {
      const dtos = await fetchCalendarEvents(year, month)
      setData(dtos.map(toCalendarEvent))
    } catch {
      setIsError(true)
    } finally {
      setIsLoading(false)
    }
  }, [year, month])

  useEffect(() => {
    load()
  }, [load])

  return { data, isLoading, isError, refetch: load }
}
