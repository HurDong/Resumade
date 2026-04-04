export interface CodingProblem {
  id: number
  company: string
  date: string
  title: string
  types: string[]
  platform: string
  level: number | null
  myApproach: string
  betterApproach: string
  betterCode: string
  note: string
}

export const MOCK_PROBLEMS: CodingProblem[] = [
  {
    id: 1,
    company: "카카오",
    date: "2024-11-02",
    title: "메뉴 리뉴얼",
    types: ["조합", "HashMap"],
    platform: "프로그래머스",
    level: 2,
    myApproach:
      "완전탐색으로 모든 조합 생성 후 Map으로 빈도 카운팅. 정렬 후 String 변환에서 막혔고, 두 코스 이상 주문 조건을 처음에 놓쳤음.",
    betterApproach:
      "itertools.combinations으로 코스별 조합 생성, Counter로 빈도 카운팅. order별 sorted 후 join으로 키 생성하는 게 핵심.",
    betterCode: `from itertools import combinations
from collections import Counter

def solution(orders, course):
    answer = []
    for c in course:
        counter = Counter()
        for order in orders:
            for combo in combinations(sorted(order), c):
                counter[''.join(combo)] += 1

        if counter:
            max_count = max(counter.values())
            if max_count >= 2:
                answer += [menu for menu, cnt in counter.items()
                           if cnt == max_count]
    return sorted(answer)`,
    note: "두 코스 이상 조건(max_count >= 2) 빠뜨리면 WA. sorted 후 join이 핵심 패턴.",
  },
  {
    id: 2,
    company: "네이버",
    date: "2024-09-15",
    title: "경사로",
    types: ["구현", "시뮬레이션"],
    platform: "백준",
    level: null,
    myApproach:
      "행/열 분리 처리는 했는데 경사로 중복 설치 체크에서 계속 틀렸음. 오르막/내리막 방향에 따라 경사로 위치가 반대라는 걸 늦게 파악함. 시간 부족으로 미완성.",
    betterApproach:
      "used 배열로 경사로 설치 여부 관리. 오르막(diff=1)은 이전 L칸, 내리막(diff=-1)은 다음 L칸에 경사로. 조건 범위 벗어나거나 이미 used면 False 반환.",
    betterCode: `def can_place(row, L):
    n = len(row)
    used = [False] * n

    for i in range(n - 1):
        diff = row[i+1] - row[i]
        if abs(diff) > 1:
            return False
        if diff == 1:  # 오르막: 이전 L칸
            for j in range(i - L + 1, i + 1):
                if j < 0 or used[j] or row[j] != row[i]:
                    return False
                used[j] = True
        elif diff == -1:  # 내리막: 다음 L칸
            for j in range(i + 1, i + L + 1):
                if j >= n or used[j] or row[j] != row[i+1]:
                    return False
                used[j] = True
    return True`,
    note: "방향별 경사로 위치가 반대임을 꼭 기억. used 배열 중복 방지가 핵심. 재풀이 필요.",
  },
  {
    id: 3,
    company: "토스",
    date: "2024-10-20",
    title: "섬 연결하기",
    types: ["MST", "크루스칼", "Union-Find"],
    platform: "프로그래머스",
    level: 3,
    myApproach:
      "처음에 프림 알고리즘으로 시도했다가 크루스칼로 전환. Union-Find path compression 적용해서 무난하게 풀었음.",
    betterApproach:
      "간선 정렬 후 Union-Find로 사이클 없는 간선만 MST에 추가. path compression 필수 (없으면 대형 케이스 TLE).",
    betterCode: `def solution(n, costs):
    costs.sort(key=lambda x: x[2])
    parent = list(range(n))

    def find(x):
        if parent[x] != x:
            parent[x] = find(parent[x])
        return parent[x]

    def union(a, b):
        a, b = find(a), find(b)
        if a != b:
            parent[b] = a
            return True
        return False

    ans = 0
    for u, v, w in costs:
        if union(u, v):
            ans += w
    return ans`,
    note: "path compression 없으면 TLE. union 반환값이 False = 사이클 = 스킵.",
  },
  {
    id: 4,
    company: "라인",
    date: "2023-06-20",
    title: "징검다리 건너기",
    types: ["이진탐색", "파라메트릭 서치"],
    platform: "프로그래머스",
    level: 3,
    myApproach:
      "슬라이딩 윈도우로 접근했다가 최솟값 트래킹이 복잡해서 파라메트릭 서치로 전환. is_possible(mid) 함수 정의하니 깔끔하게 풀렸음.",
    betterApproach:
      "이진탐색으로 '건널 수 있는 최대 인원'을 탐색. can_cross(mid): stone - mid <= 0인 연속 구간이 k 이상이면 False.",
    betterCode: `def solution(stones, k):
    def can_cross(mid):
        count = 0
        for stone in stones:
            if stone - mid <= 0:
                count += 1
                if count >= k:
                    return False
            else:
                count = 0
        return True

    left, right = 1, max(stones)
    answer = 1
    while left <= right:
        mid = (left + right) // 2
        if can_cross(mid):
            answer = mid
            left = mid + 1
        else:
            right = mid - 1
    return answer`,
    note: "파라메트릭 서치 정석 패턴. is_possible(mid) 함수 설계가 핵심. 외워둘 것.",
  },
  {
    id: 5,
    company: "카카오",
    date: "2023-09-09",
    title: "셔틀버스",
    types: ["정렬", "시뮬레이션", "그리디"],
    platform: "프로그래머스",
    level: 3,
    myApproach:
      "시간을 분 단위 정수로 변환해서 처리. 정렬 후 버스 시간대별로 탑승자 시뮬레이션. 마지막 버스 분기 처리가 약간 헷갈렸지만 풀어냄.",
    betterApproach:
      "마지막 버스에서: 정원 미달이면 버스 출발시간, 만석이면 마지막 탑승자 -1분. 시간을 분 정수로 다루는 게 핵심.",
    betterCode: `def solution(n, t, m, timetable):
    times = sorted(int(x[:2])*60 + int(x[3:]) for x in timetable)
    bus_time = 540
    idx = 0

    for i in range(n):
        count = 0
        while idx < len(times) and times[idx] <= bus_time and count < m:
            count += 1
            idx += 1

        if i == n - 1:
            answer = bus_time if count < m else times[idx - 1] - 1
        bus_time += t

    h, m_ = divmod(answer, 60)
    return f"{h:02d}:{m_:02d}"`,
    note: "시간을 분 정수로 변환이 핵심. 마지막 버스 분기 조건을 명확히 이해해야.",
  },
]
