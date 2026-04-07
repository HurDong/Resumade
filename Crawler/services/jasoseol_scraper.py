import httpx
import asyncio

class JasoseolScraper:
    def __init__(self):
        # 자소설 API 베이스
        self.base_headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Accept": "application/json, text/plain, */*",
        }
        
    async def scrape_recent_jobs(self, year: int = None, month: int = None):
        """
        1. 전체 채용 달력 (목록) 무제한으로 가져오기 (IP차단 방지를 위해 문항은 제외)
        """
        jobs_data = []
        
        async with httpx.AsyncClient(headers=self.base_headers, timeout=10.0) as client:
            # Step 1: 달력 목록 가져오기 (가상의 오픈 API 주소 가정)
            # 자소설닷컴의 실제 공고 리스트 API 엔드포인트 세팅 (추후 분석된 정확한 URL로 수정 가능)
            try:
                # 사용자님이 찾아주신 진짜 URL! (POST 요청임에 주의)
                calendar_url = "https://jasoseol.com/employment/calendar_list.json"
                
                import datetime
                import calendar
                
                if year and month:
                    first_day = datetime.datetime(year, month, 1)
                    _, last_day_num = calendar.monthrange(year, month)
                    last_day = datetime.datetime(year, month, last_day_num)
                    
                    # 자소설닷컴 API 스펙에 맞춤 (UTC 기준 15:00:00)
                    start_time = (first_day - datetime.timedelta(days=7)).strftime("%Y-%m-%dT15:00:00.000Z")
                    end_time = (last_day + datetime.timedelta(days=7)).strftime("%Y-%m-%dT15:00:00.000Z")
                else:
                    # 파라미터가 없으면 기존처럼 현재 기준 앞뒤
                    now = datetime.datetime.utcnow()
                    start_time = (now - datetime.timedelta(days=15)).strftime("%Y-%m-%dT15:00:00.000Z")
                    end_time = (now + datetime.timedelta(days=45)).strftime("%Y-%m-%dT15:00:00.000Z")
                
                # 자소설이 요구하는 필수 페이로드(기간) 장착
                payload = {
                    "start_time": start_time,
                    "end_time": end_time
                }
                
                resp = await client.post(calendar_url, json=payload)
                
                if resp.status_code == 200:
                    raw_data = resp.json()
                    print(f"[DEBUG] Jasoseol Response Keys: {list(raw_data.keys())}")
                    if "employment" not in raw_data and "employments" not in raw_data:
                         print(f"[DEBUG] Full Response: {str(raw_data)[:500]}")
                    
                    # JSON 구조 파싱
                    employments = raw_data.get("employment", []) or raw_data.get("employments", [])
                    print(f"[DEBUG] Parsed employments length: {len(employments)}")
                    # [역공학] 자소설닷컴의 division 수학적 파싱 (신입,경력,인턴 감별기)
                    for job in employments:
                        nested_emps = job.get("employments", [])
                        career_types = set()
                        job_groups = set()
                        for ne in nested_emps:
                            div = ne.get("division")
                            if div is not None:
                                career_types.add(div)
                                
                            for dg in ne.get("duty_groups", []):
                                gid = dg.get("group_id")
                                if gid is not None:
                                    job_groups.add(gid)
                        
                        if len(nested_emps) > 0 and nested_emps[0].get("id") is not None:
                            job["id"] = nested_emps[0].get("id")
                            
                        job["career_types"] = list(career_types)
                        job["job_groups"] = list(job_groups)
                    if len(employments) == 0:
                         print("[WARN] Received 0 items. Possibly due to missing payload or token. Triggering fallback.")
                         employments = self._get_mock_employments()
                else:
                    print(f"[ERROR] API returned {resp.status_code}. Full text: {resp.text[:200]}")
                    # 서버 봇 차단 대비 (가짜 목업 데이터 생성하여 테스트 속행)
                    employments = self._get_mock_employments()
            except Exception as e:
                print(f"List Fetch Failed: {e}, using mock data fallback.")
                employments = self._get_mock_employments()

            return employments

    async def scrape_questions_for_job_id(self, employment_id: int):
        """
        특정 공고(employment_id) 1개에 대해서만 자소서 문항 배열 가져오기 (Spring Boot 스크랩 시 호출)
        """
        post_url = "https://jasoseol.com/employment/employment_question.json"
        
        async with httpx.AsyncClient(headers=self.base_headers, timeout=10.0) as client:
            try:
                payload = {"employment_id": employment_id, "employment_resume_id": employment_id}
                res = await client.post(post_url, json=payload)
                if res.status_code == 200:
                    q_data = res.json()
                    return q_data.get("employment_question", [])
                else:
                    return []
            except Exception as e:
                print(f"Error scraping questions: {e}")
                return []

    async def _fetch_questions_for_job(self, client: httpx.AsyncClient, job: dict):
        """
        [특정 공고 1개의 질문 배열 가져오기]
        사용자님이 찾아주신 POST https://jasoseol.com/employment/employment_question.json 를 찌릅니다.
        """
        # 해당 공고의 이력서(직무) id 뽑기. 실제 페이로드 구조에 따라 수정 필요
        employment_id = job.get("id")
        
        # 아까 사용자님이 찾아준 핵심 엔드포인트
        post_url = "https://jasoseol.com/employment/employment_question.json"
        
        try:
            # 페이로드 구조 (사용자 확인 전이므로 표준형식 가정)
            payload = {"employment_id": employment_id, "employment_resume_id": employment_id}
            
            res = await client.post(post_url, json=payload)
            if res.status_code == 200:
                q_data = res.json()
                questions = q_data.get("employment_question", [])
                job["questions"] = questions
            else:
                job["questions"] = [{"question": "문항 데이터를 불러오는 데 실패했습니다 (차단됨).", "word_limit": 0}]
        except Exception as e:
            job["questions"] = []
            
        return job

    def _get_mock_employments(self):
        """실제 API가 차단(403)되었을 때 클라이언트 UI 테스트를 위한 가짜 데이터"""
        return [
            {
                "id": 417223,
                "name": "에코마케팅",
                "title": "개발자 공개 채용(백엔드, 프론트엔드)",
                "start_time": "2026-04-06T15:51:00.000+09:00",
                "end_time": "2026-04-19T23:59:00.000+09:00",
                "image_file_name": "https://daoift3qrrnil.cloudfront.net/employment_companies/images/000/101/853/original/...",
                "company_group": {"name": "에코마케팅"},
                "career_types": [1, 5],
                "job_groups": []
            },
            {
                "id": 102311,
                "name": "NHN Cloud",
                "title": "AI 전환 백엔드 개발",
                "start_time": "2026-04-01T09:00:00.000+09:00",
                "end_time": "2026-05-15T23:59:00.000+09:00",
                "image_file_name": "",
                "company_group": {"name": "NHN Cloud"},
                "career_types": [2],
                "job_groups": []
            }
        ]
