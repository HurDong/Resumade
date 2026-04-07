from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import uvicorn
from routers.import_api import router as import_router

app = FastAPI(title="Resumade Crawler API", description="자소설닷컴 및 각종 채용 플랫폼 스크래핑 마이크로서비스")

# CORS 설정 (Next.js 로컬호스트 접근 허용)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 로컬 개발 목적
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(import_router, prefix="/api/v1/crawl")

@app.get("/")
def read_root():
    return {"status": "Crawler is heavily breathing... 🚀"}

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
