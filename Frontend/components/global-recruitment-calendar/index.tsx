"use client";

import { useState, useEffect } from "react";
import { format, differenceInDays, addMonths, subMonths, startOfMonth, endOfMonth, startOfWeek, endOfWeek, eachDayOfInterval, isSameMonth, isSameDay, isToday } from "date-fns";
import { ko } from "date-fns/locale";
import { Calendar, Clock, Briefcase, Building2, DownloadCloud, Loader2, ListTodo, LayoutGrid, ChevronLeft, ChevronRight, CheckCircle2 } from "lucide-react";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";

interface JasoseolJob {
  id: number;
  name: string;
  title: string;
  start_time: string;
  end_time: string;
  image_file_name?: string;
  career_types?: number[];
  job_groups?: number[];
}

export function GlobalRecruitmentCalendar({ onImportSuccess }: { onImportSuccess: () => void }) {
  const [parsedJobs, setParsedJobs] = useState<JasoseolJob[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<"CALENDAR" | "KANBAN">("CALENDAR");
  const [currentDate, setCurrentDate] = useState<Date>(new Date());
  const [searchTerm, setSearchTerm] = useState("");
  const [careerFilter, setCareerFilter] = useState("전체");

  const [selectedJobToImport, setSelectedJobToImport] = useState<JasoseolJob | null>(null);
  const [importing, setImporting] = useState(false);

  const careerOptions = ["전체", "신입", "경력", "인턴"];

  const fetchGlobalJobs = async (date: Date) => {
    try {
      setLoading(true); setError(null);
      const year = format(date, "yyyy");
      const month = format(date, "M");
      const res = await fetch(`http://localhost:8000/api/v1/crawl/jasoseol?year=${year}&month=${month}`);
      if (!res.ok) throw new Error(`서버 응답 오류 (상태코드: ${res.status})`);
      const result = await res.json();
      if (result.status === "success" && Array.isArray(result.data)) {
        setParsedJobs(result.data);
      } else {
        throw new Error("올바른 파이썬 서버 응답 포맷이 아닙니다.");
      }
    } catch (err: any) {
      setError(`크롤링 실패: 파이썬 서버가 켜져 있는지 확인해주세요. \n${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchGlobalJobs(currentDate);
  }, [currentDate]);

  const handlePrevMonth = () => setCurrentDate(subMonths(currentDate, 1));
  const handleNextMonth = () => setCurrentDate(addMonths(currentDate, 1));

  const handleImport = async () => {
    if (!selectedJobToImport) return;
    setImporting(true);
    try {
      // 1. Python 서버에서 자소서 문항 파싱 (단건 처리)
      const qRes = await fetch(`http://localhost:8000/api/v1/crawl/jasoseol/questions/${selectedJobToImport.id}`);
      let questionsData = [];
      if (qRes.ok) {
        const result = await qRes.json();
        questionsData = result.data;
      }
      
      // 2. Spring Boot DB로 Import
      const payload = {
        id: selectedJobToImport.id,
        name: selectedJobToImport.name,
        title: selectedJobToImport.title,
        end_time: selectedJobToImport.end_time,
        image_file_name: selectedJobToImport.image_file_name,
        questions: questionsData
      };
      
      const res = await fetch("http://localhost:8080/api/applications/import-jasoseol", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });
      
      if (res.ok) {
         setSelectedJobToImport(null);
         onImportSuccess(); // 대시보드 뷰로 변경하라는 콜백
      } else {
         alert("스크랩 저장에 실패했습니다.");
      }
    } catch (err) {
      console.error(err);
      alert("스크랩 중 오류가 발생했습니다.");
    } finally {
      setImporting(false);
    }
  };

  const filteredJobs = parsedJobs.filter((job) => {
    const matchSearch = searchTerm === "" || 
      (job.name && job.name.toLowerCase().includes(searchTerm.toLowerCase())) || 
      (job.title && job.title.toLowerCase().includes(searchTerm.toLowerCase()));

    let matchCareer = true;
    if (careerFilter !== "전체") {
      const types = job.career_types || [];
      if (careerFilter === "신입") {
        matchCareer = types.includes(1) || types.includes(5);
      } else if (careerFilter === "경력") {
        matchCareer = types.includes(2) || types.includes(5);
      } else if (careerFilter === "인턴") {
        matchCareer = types.includes(3);
      }
    }
    return matchSearch && matchCareer;
  });

  const monthStart = startOfMonth(currentDate);
  const monthEnd = endOfMonth(monthStart);
  const startDate = startOfWeek(monthStart);
  const endDate = endOfWeek(monthEnd);
  
  const calendarDays = eachDayOfInterval({ start: startDate, end: endDate });
  const weekDays = ["일", "월", "화", "수", "목", "금", "토"];

  return (
    <div className="space-y-8 animate-in fade-in duration-500 h-full overflow-y-auto w-full custom-scrollbar pb-10">
      {/* Header / Nav */}
      <div className="flex flex-col md:flex-row items-center justify-between gap-6 bg-background/50 border border-border/50 rounded-2xl p-6 shadow-sm backdrop-blur-sm">
        <div className="space-y-1">
          <h1 className="text-xl font-extrabold bg-gradient-to-r from-orange-400 to-amber-500 bg-clip-text text-transparent flex items-center gap-2">
            <Calendar className="w-6 h-6 text-orange-400" />
            스마트 채용 달력 (전체 공고)
          </h1>
          <p className="text-muted-foreground text-sm">
            클릭해서 공고를 확인하고 내 대시보드로 즉시 스크랩할 수 있습니다.
          </p>
        </div>
        
        <div className="flex items-center gap-6">
          <div className="flex bg-muted p-1 rounded-lg border border-border/50">
            <button onClick={() => setViewMode("CALENDAR")} className={`px-4 py-2 rounded flex items-center gap-2 text-sm font-bold transition-all ${viewMode === "CALENDAR" ? "bg-background text-foreground shadow" : "text-muted-foreground"}`}>
              <Calendar className="w-4 h-4" /> 달력
            </button>
            <button onClick={() => setViewMode("KANBAN")} className={`px-4 py-2 rounded flex items-center gap-2 text-sm font-bold transition-all ${viewMode === "KANBAN" ? "bg-background text-foreground shadow" : "text-muted-foreground"}`}>
              <LayoutGrid className="w-4 h-4" /> 리스트
            </button>
          </div>
          <div className="flex items-center gap-4 bg-muted px-4 py-2 rounded-xl border border-border/50">
            <button onClick={handlePrevMonth} disabled={loading} className="p-1 hover:bg-background/80 rounded transition text-foreground"><ChevronLeft className="w-6 h-6" /></button>
            <h2 className="text-xl font-black w-32 text-center text-foreground">{format(currentDate, "yyyy.MM")}</h2>
            <button onClick={handleNextMonth} disabled={loading} className="p-1 hover:bg-background/80 rounded transition text-foreground"><ChevronRight className="w-6 h-6" /></button>
          </div>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="bg-background/50 border border-border/50 rounded-2xl p-4 flex flex-col md:flex-row gap-4 items-center relative">
         {loading && (
           <div className="absolute inset-0 bg-background/80 backdrop-blur-sm z-10 flex items-center justify-center rounded-2xl">
              <Loader2 className="w-6 h-6 animate-spin text-orange-500 mr-2" />
              <span className="font-bold text-orange-400 text-sm">데이터를 실시간으로 받아오고 있습니다...</span>
           </div>
         )}
         <input type="text" placeholder="🔍 기업명, 직무명으로 검색" className="flex-1 bg-background border border-input text-foreground px-4 py-3 rounded-lg focus:outline-none focus:border-orange-500 transition-colors" value={searchTerm} onChange={e => setSearchTerm(e.target.value)} />
         <select className="w-full md:w-48 bg-background border border-input text-foreground px-4 py-3 rounded-lg focus:border-orange-500 appearance-none transition-colors" value={careerFilter} onChange={e => setCareerFilter(e.target.value)}>
           {careerOptions.map(opt => <option key={opt} value={opt}>{opt} 채용만 보기</option>)}
         </select>
      </div>

      {/* View Mode */}
      {viewMode === "CALENDAR" ? (
        <div className="bg-background border border-border/50 rounded-2xl overflow-hidden shadow-sm relative">
          <div className="grid grid-cols-7 bg-muted/30 border-b border-border/50">
            {weekDays.map(day => (
              <div key={day} className={`p-3 text-center text-sm font-bold ${day === '일' ? 'text-red-400' : day === '토' ? 'text-blue-400' : 'text-muted-foreground'}`}>{day}</div>
            ))}
          </div>
          <div className="grid grid-cols-7 auto-rows-[minmax(120px,180px)] bg-border/50 gap-[1px]">
            {calendarDays.map((day, idx) => {
              const jobsEndingToday = filteredJobs.filter(job => isSameDay(new Date(job.end_time), day));
              const isCurrentMonth = isSameMonth(day, monthStart);
              const today = isToday(day);
              return (
                <div key={idx} className={`bg-background p-2 flex flex-col ${!isCurrentMonth ? 'opacity-40 bg-muted/20' : 'hover:bg-muted/10'} transition-colors`}>
                  <div className={`text-right text-sm font-bold mb-2 ${today ? 'text-orange-500' : 'text-muted-foreground'}`}>
                    <span className={today ? "bg-orange-500/10 px-2 py-0.5 rounded-full" : ""}>{format(day, "d")}</span>
                  </div>
                  <div className="space-y-1.5 flex-1 overflow-y-auto custom-scrollbar">
                    {jobsEndingToday.map(job => (
                       <div key={job.id} onClick={() => setSelectedJobToImport(job)} className="text-[11px] font-medium bg-muted/30 border border-border/50 hover:border-orange-500/50 hover:text-orange-500 rounded px-1.5 py-1 truncate cursor-pointer transition-all flex items-center text-foreground/80">
                         <span className="w-1.5 h-1.5 bg-orange-500 rounded-full mr-1.5 shrink-0" />
                         <span className="truncate">{job.name}</span>
                       </div>
                    ))}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {filteredJobs.map(job => {
            const endDate = new Date(job.end_time);
            const dDay = differenceInDays(endDate, new Date());
            const isClosed = dDay < 0;
            return (
              <div key={job.id} onClick={() => setSelectedJobToImport(job)} className="group bg-background border border-border/50 hover:border-orange-500/50 rounded-xl overflow-hidden transition-all hover:shadow-md cursor-pointer flex flex-col p-5">
                 <div className="flex justify-between items-start mb-4">
                   <div className="w-10 h-10 bg-white rounded-lg p-1 border border-border flex items-center justify-center">
                      {job.image_file_name ? <img src={job.image_file_name} alt={job.name} className="object-contain w-full h-full" /> : <Building2 className="text-neutral-400 w-5 h-5" />}
                   </div>
                   <div className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold ${isClosed ? 'bg-muted text-muted-foreground' : 'bg-orange-500/10 text-orange-600 border border-orange-200'}`}>
                      {isClosed ? '마감됨' : `D-${dDay}`}
                   </div>
                 </div>
                 <h3 className="text-sm font-bold text-foreground line-clamp-2 leading-snug">{job.title}</h3>
                 <p className="text-muted-foreground text-xs mt-1 mb-4 truncate">{job.name}</p>
                 <div className="mt-auto pt-4 border-t border-border/50 space-y-3">
                    <div className="flex items-center gap-1.5 text-xs text-muted-foreground font-medium">
                       <Calendar className="w-3.5 h-3.5" /> 마감: {format(endDate, "MM.dd HH:mm", { locale: ko })}
                    </div>
                 </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Import Dialog */}
      <Dialog open={!!selectedJobToImport} onOpenChange={(open) => !importing && !open && setSelectedJobToImport(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="text-xl text-orange-500 flex items-center gap-2"><DownloadCloud className="w-5 h-5"/> 내 대시보드로 스크랩</DialogTitle>
            <DialogDescription>
              해당 공고의 자소서 문항을 파싱하여 서류 전형 보드로 저장합니다.
            </DialogDescription>
          </DialogHeader>
          {selectedJobToImport && (
            <div className="py-2">
              <div className="flex gap-4 items-center bg-muted/30 p-3 rounded-lg border border-border/50">
                 <div className="w-12 h-12 bg-white rounded-md p-1 border border-border">
                   {selectedJobToImport.image_file_name ? <img src={selectedJobToImport.image_file_name} className="w-full h-full object-contain" /> : <Building2 className="w-full h-full text-neutral-300 p-2" />}
                 </div>
                 <div>
                    <h3 className="font-bold text-sm line-clamp-1">{selectedJobToImport.title}</h3>
                    <p className="text-xs text-muted-foreground">{selectedJobToImport.name}</p>
                 </div>
              </div>
            </div>
          )}
          <DialogFooter className="flex gap-2">
            <Button variant="outline" onClick={() => setSelectedJobToImport(null)} disabled={importing}>취소</Button>
            <Button onClick={handleImport} disabled={importing} className="bg-orange-600 hover:bg-orange-700 text-white">
              {importing ? <><Loader2 className="w-4 h-4 mr-2 animate-spin" /> 문항 파싱 중...</> : "내 보드로 스크랩"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
