import httpx
from fastapi import APIRouter, HTTPException
from services.jasoseol_scraper import JasoseolScraper

router = APIRouter()
scraper = JasoseolScraper()

@router.get("/jasoseol")
async def crawl_jasoseol_data(year: int = None, month: int = None):
    """
    자소설닷컴 API를 호출하여 해당 월의 공고(달력) 목록을 전체 가져옵니다. (문항 제외)
    """
    try:
        results = await scraper.scrape_recent_jobs(year=year, month=month)
        return {
            "status": "success", 
            "count": len(results), 
            "data": results
        }
    except Exception as e:
        print(f"Error during scraping: {e}")
        raise HTTPException(status_code=500, detail=f"Scraping failed: {str(e)}")

@router.get("/jasoseol/questions/{job_id}")
async def get_jasoseol_questions(job_id: int):
    """
    특정 공고의 자소서 문항들만 가져옵니다.
    """
    try:
        questions = await scraper.scrape_questions_for_job_id(job_id)
        return {
            "status": "success",
            "data": questions
        }
    except Exception as e:
        print(f"Error fetching questions: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to fetch questions: {str(e)}")
