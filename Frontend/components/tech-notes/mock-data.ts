export interface TechTemplate {
  id: number
  title: string
  category: string
  summary: string
  conditions: string[]
  template: string
  tags: string[]
}

export const TECH_CATEGORIES = [
  "전체",
  "문법·유틸",
  "그래프 탐색",
  "최단 경로",
  "동적 프로그래밍",
  "자료구조",
  "탐색·정렬",
  "위상 정렬",
]

export const MOCK_TEMPLATES: TechTemplate[] = [
  // ───────────────────────── 문법·유틸 ─────────────────────────
  {
    id: "s1",
    title: "collections.deque",
    category: "문법·유틸",
    summary: "양방향 O(1) 큐. BFS 필수, list.pop(0) 대체",
    conditions: [
      "append(x) / appendleft(x) — 오른쪽 / 왼쪽 추가",
      "pop() / popleft() — O(1), list.pop(0)은 O(N)이라 절대 금지",
      "BFS에서 queue = deque([start]) 로 시작",
      "슬라이딩 윈도우 최댓값에는 Monotonic Deque 패턴 사용",
    ],
    template: `from collections import deque

# BFS 기본 큐
queue = deque([start])
visited.add(start)

while queue:
    node = queue.popleft()        # O(1)
    for neighbor in graph[node]:
        if neighbor not in visited:
            visited.add(neighbor)
            queue.append(neighbor)

# Monotonic Deque — 슬라이딩 윈도우 최댓값
def max_sliding_window(arr, k):
    dq = deque()   # (값, 인덱스)
    result = []
    for i, val in enumerate(arr):
        while dq and dq[0][1] <= i - k:
            dq.popleft()           # 범위 밖 제거
        while dq and dq[-1][0] <= val:
            dq.pop()               # 현재 값보다 작은 것 제거
        dq.append((val, i))
        if i >= k - 1:
            result.append(dq[0][0])
    return result`,
    tags: ["BFS", "큐", "슬라이딩윈도우"],
  },
  {
    id: "s2",
    title: "collections.Counter",
    category: "문법·유틸",
    summary: "빈도 카운팅 + most_common(). 없는 키는 0 반환",
    conditions: [
      "Counter(iterable) — 문자열·리스트 모두 사용 가능",
      "counter.most_common(k) — 빈도 상위 k개 반환",
      "없는 키 접근 시 0 반환 (KeyError 없음)",
      "counter1 - counter2 연산 가능 (음수 결과 제거됨)",
    ],
    template: `from collections import Counter

# 기본
counter = Counter("aabbcc")      # Counter({'a':2,'b':2,'c':2})
counter = Counter([1,1,2,3,1])   # Counter({1:3, 2:1, 3:1})

# 상위 K개
top2 = counter.most_common(2)    # [(1,3),(2,1)]

# 없는 키 → 0
print(counter[99])               # 0 (KeyError 없음)

# Counter 활용 (프로그래머스 패턴)
from itertools import combinations
orders, course = ["XYZ","XWY","WXA"], [2,3]
answer = []
for c in course:
    cnt = Counter()
    for order in orders:
        for combo in combinations(sorted(order), c):
            cnt[''.join(combo)] += 1
    if cnt:
        mx = max(cnt.values())
        if mx >= 2:
            answer += [m for m, v in cnt.items() if v == mx]
print(sorted(answer))`,
    tags: ["빈도카운팅", "most_common"],
  },
  {
    id: "s3",
    title: "collections.defaultdict",
    category: "문법·유틸",
    summary: "KeyError 없는 딕셔너리. 그래프·빈도 저장에 완벽",
    conditions: [
      "defaultdict(list) — 기본값 [], graph 표현에 사용",
      "defaultdict(int) — 기본값 0, 빈도 카운팅에 사용",
      "초기화 불필요: graph[u].append(v) 바로 호출 가능",
      "일반 dict로 변환: dict(defaultdict_obj)",
    ],
    template: `from collections import defaultdict

# 무방향 그래프
graph = defaultdict(list)
for u, v in edges:
    graph[u].append(v)
    graph[v].append(u)

# 빈도 카운팅
freq = defaultdict(int)
for x in arr:
    freq[x] += 1   # .get(x, 0)+1 불필요

# 중첩 defaultdict
nested = defaultdict(lambda: defaultdict(int))
nested['a']['b'] += 1  # 자동 생성

# 일반 dict 변환
result = dict(freq)`,
    tags: ["그래프", "빈도카운팅", "KeyError방지"],
  },
  {
    id: "s4",
    title: "itertools — 조합/순열",
    category: "문법·유틸",
    summary: "combinations · permutations · product. 완전탐색 기본",
    conditions: [
      "combinations(arr, r) — 순서 무관, 중복 없음 (nCr)",
      "permutations(arr, r) — 순서 있음 (nPr), n! 폭발 주의",
      "combinations_with_replacement(arr, r) — 중복 허용 조합",
      "product(arr, repeat=n) — 중복 순열 (데카르트 곱)",
    ],
    template: `from itertools import combinations, permutations
from itertools import combinations_with_replacement, product

arr = [1, 2, 3]

# 조합 (순서 무관, 중복 없음)
list(combinations(arr, 2))   # [(1,2),(1,3),(2,3)]

# 순열 (순서 있음)
list(permutations(arr, 2))   # [(1,2),(1,3),(2,1),(2,3),(3,1),(3,2)]

# 중복 조합
list(combinations_with_replacement([1,2], 2))
# [(1,1),(1,2),(2,2)]

# 중복 순열 (데카르트 곱)
list(product([0,1], repeat=3))
# [(0,0,0),(0,0,1),...,(1,1,1)]  — 비트마스크 순회

# 직접 백트래킹 (메모리 효율)
result = []
def backtrack(start, path):
    if len(path) == r:
        result.append(path[:])
        return
    for i in range(start, len(arr)):
        path.append(arr[i])
        backtrack(i + 1, path)
        path.pop()
backtrack(0, [])`,
    tags: ["완전탐색", "조합", "순열"],
  },
  {
    id: "s5",
    title: "heapq — 우선순위 큐",
    category: "문법·유틸",
    summary: "최솟값 O(log N). 다익스트라·그리디 필수",
    conditions: [
      "Python heapq는 기본 최소 힙",
      "최대 힙: 값에 -1 곱해서 넣고, 꺼낼 때 다시 -1 곱하기",
      "heappush(heap, (cost, node)) — 튜플 첫 원소로 정렬",
      "Dijkstra: if cost > dist[node]: continue 반드시 체크",
    ],
    template: `import heapq

# 최소 힙
heap = []
heapq.heappush(heap, 3)
heapq.heappush(heap, 1)
print(heapq.heappop(heap))   # 1

# 최대 힙 (음수 트릭)
max_heap = []
for v in [3, 1, 5]:
    heapq.heappush(max_heap, -v)
print(-heapq.heappop(max_heap))  # 5

# 리스트 → 힙 O(N)
arr = [4, 2, 7, 1]
heapq.heapify(arr)

# 커스텀 우선순위
heapq.heappush(heap, (cost, node_id))
cost, node_id = heapq.heappop(heap)

# nsmallest / nlargest
heapq.nsmallest(3, arr)  # 가장 작은 3개
heapq.nlargest(3, arr)`,
    tags: ["우선순위큐", "최솟값", "다익스트라"],
  },
  {
    id: "s6",
    title: "bisect — 이진탐색 라이브러리",
    category: "문법·유틸",
    summary: "정렬 배열에서 O(log N) 위치 탐색. 범위 쿼리에 유용",
    conditions: [
      "반드시 정렬된 배열에서만 사용",
      "bisect_left(arr, x) — x 이상인 첫 번째 인덱스 (좌측 경계)",
      "bisect_right(arr, x) — x 초과인 첫 번째 인덱스 (우측 경계)",
      "x 개수: bisect_right - bisect_left",
    ],
    template: `import bisect

arr = [1, 2, 2, 2, 3, 4, 5]

bisect.bisect_left(arr, 2)    # 1 — arr[1]이 첫 2
bisect.bisect_right(arr, 2)   # 4 — 2가 끝나는 위치

# 개수 세기
count = bisect.bisect_right(arr, 2) - bisect.bisect_left(arr, 2)  # 3

# 정렬 유지하며 삽입
bisect.insort(arr, 2.5)

# 범위 [l, r] 내 원소 개수
def count_range(arr, l, r):
    return bisect.bisect_right(arr, r) - bisect.bisect_left(arr, l)

# 존재 여부 확인
def contains(arr, x):
    i = bisect.bisect_left(arr, x)
    return i < len(arr) and arr[i] == x`,
    tags: ["이진탐색", "범위쿼리", "정렬배열"],
  },
  {
    id: "s7",
    title: "sorted / sort — 커스텀 정렬",
    category: "문법·유틸",
    summary: "lambda 키로 다중 기준 정렬. sorted()는 새 리스트 반환",
    conditions: [
      "sorted() → 새 리스트, .sort() → in-place",
      "key=lambda x: (기준1, 기준2) 로 다중 기준",
      "역순은 -x 또는 reverse=True",
      "Python Timsort는 stable — 동일 키는 원래 순서 유지",
    ],
    template: `# 단일 기준
arr = [3, 1, 4, 1, 5]
sorted(arr)                      # [1, 1, 3, 4, 5]
sorted(arr, reverse=True)        # [5, 4, 3, 1, 1]

# lambda 정렬
people = [(25,"Alice"),(20,"Bob"),(25,"Charlie")]
sorted(people, key=lambda x: x[0])
# [(20,'Bob'), (25,'Alice'), (25,'Charlie')]

# 다중 기준: 나이 오름 → 이름 내림
sorted(people, key=lambda x: (x[0], [-ord(c) for c in x[1]]))

# 문자열 길이 + 알파벳
words = ["banana","apple","kiwi","fig"]
sorted(words, key=lambda x: (len(x), x))
# ['fig', 'kiwi', 'apple', 'banana']

# in-place sort
arr.sort(key=lambda x: -x)   # 내림차순`,
    tags: ["정렬", "lambda", "다중기준"],
  },
  {
    id: "s8",
    title: "sys.stdin — 빠른 입력",
    category: "문법·유틸",
    summary: "백준 TLE 방지. input() 대비 약 10배 빠름",
    conditions: [
      "sys.stdin.readline()은 개행 포함 → .strip() 또는 .rstrip() 필수",
      "프로그래머스는 불필요, 백준 대형 케이스에서 필수",
      "전체를 한 번에: data = sys.stdin.read().split()",
      "input = sys.stdin.readline 으로 별칭 설정 후 기존 코드 그대로 사용 가능",
    ],
    template: `import sys
input = sys.stdin.readline  # 전역 대체

# 정수 하나
n = int(input())

# 여러 정수 한 줄
a, b = map(int, input().split())

# n개 줄 입력
arr = [int(input()) for _ in range(n)]

# 2D 행렬
matrix = [list(map(int, input().split())) for _ in range(n)]

# 모든 입력 한 번에 (가장 빠름)
data = sys.stdin.read().split()
idx = 0
n = int(data[idx]); idx += 1
arr = [int(data[idx + i]) for i in range(n)]

# 출력 성능 (print 반복 시)
import sys
print = sys.stdout.write  # 필요 시 (문자열 직접 전달)`,
    tags: ["입출력", "백준", "TLE방지"],
  },
  {
    id: "s9",
    title: "2D 배열 초기화 & 회전",
    category: "문법·유틸",
    summary: "2D 배열 초기화 실수 방지. 전치·회전 공식 암기",
    conditions: [
      "[[0]*col for _ in range(row)] 만 사용 — [[0]*col]*row 절대 금지 (공유 참조)",
      "전치: list(map(list, zip(*matrix)))",
      "시계방향 90도: zip(*matrix[::-1]) 후 각 행 리스트 변환",
      "반시계방향 90도: 역방향 zip(*matrix) 후 각 행 리스트 변환",
    ],
    template: `# 안전한 2D 초기화
grid = [[0] * cols for _ in range(rows)]   # ✅
# grid = [[0]*cols] * rows  ← 절대 금지! (모든 행이 같은 참조)

# 잘못된 방식 증명
bad = [[0]*3] * 3
bad[0][0] = 1
print(bad)  # [[1,0,0],[1,0,0],[1,0,0]] — 모두 바뀜!

# 전치 (transpose)
transposed = list(map(list, zip(*grid)))

# 90도 시계방향 회전
cw = [list(row) for row in zip(*grid[::-1])]

# 90도 반시계방향 회전
ccw = [list(row) for row in zip(*grid)][::-1]

# 180도 회전
r180 = [row[::-1] for row in grid[::-1]]

# flatten
flat = [x for row in grid for x in row]

# 리스트 컴프리헨션 2D
pairs = [(r,c) for r in range(n) for c in range(m) if grid[r][c] == 1]`,
    tags: ["2D배열", "전치", "회전", "초기화"],
  },

  // ───────────────────────── 그래프 탐색 ─────────────────────────
  {
    id: "a1",
    title: "BFS",
    category: "그래프 탐색",
    summary: "최단 거리·레벨 탐색. 가중치 없는 그래프에서 사용",
    conditions: [
      "시작 노드를 큐에 넣은 직후 visited 마킹 (중복 방지)",
      "격자: 4방향 (dx, dy) 설정 + 범위 체크 (0 <= nr < R)",
      "다중 출발 BFS: 시작점 여러 개를 큐에 동시에 넣고 출발",
      "가중치 있으면 Dijkstra 사용 (BFS 사용 불가)",
    ],
    template: `from collections import deque

# ── 그래프 BFS ──
def bfs(graph, start, n):
    dist = [-1] * (n + 1)
    dist[start] = 0
    queue = deque([start])
    while queue:
        node = queue.popleft()
        for nxt in graph[node]:
            if dist[nxt] == -1:
                dist[nxt] = dist[node] + 1
                queue.append(nxt)
    return dist

# ── 격자 BFS (상하좌우) ──
dx = [-1, 1, 0, 0]
dy = [0, 0, -1, 1]

def grid_bfs(grid, sr, sc):
    R, C = len(grid), len(grid[0])
    visited = [[False]*C for _ in range(R)]
    dist = [[0]*C for _ in range(R)]
    queue = deque([(sr, sc)])
    visited[sr][sc] = True

    while queue:
        r, c = queue.popleft()
        for i in range(4):
            nr, nc = r + dx[i], c + dy[i]
            if 0 <= nr < R and 0 <= nc < C and not visited[nr][nc]:
                if grid[nr][nc] != 1:   # 벽(1) 제외
                    visited[nr][nc] = True
                    dist[nr][nc] = dist[r][c] + 1
                    queue.append((nr, nc))
    return dist

# ── 다중 출발 BFS ──
queue = deque()
for r, c in start_points:
    queue.append((r, c))
    dist[r][c] = 0`,
    tags: ["최단거리", "격자", "다중출발"],
  },
  {
    id: "a2",
    title: "DFS & 백트래킹",
    category: "그래프 탐색",
    summary: "모든 경로·순열·조합 탐색. 재귀 후 상태 복구 필수",
    conditions: [
      "sys.setrecursionlimit(10**6) — 재귀 깊이 초과 방지",
      "백트래킹: 재귀 호출 후 path.pop() / visited[i]=False 복구",
      "순열: 모든 인덱스에서 시작 가능",
      "조합: start 포인터 사용해서 중복 방지",
    ],
    template: `import sys
sys.setrecursionlimit(10**6)

# ── 순열 ──
result = []
def perm(path, visited, arr):
    if len(path) == len(arr):
        result.append(path[:])
        return
    for i in range(len(arr)):
        if not visited[i]:
            visited[i] = True
            path.append(arr[i])
            perm(path, visited, arr)
            path.pop()
            visited[i] = False

# ── 조합 ──
def comb(start, path, r, arr):
    if len(path) == r:
        result.append(path[:])
        return
    for i in range(start, len(arr)):
        path.append(arr[i])
        comb(i + 1, path, r, arr)   # i+1: 중복 없이
        path.pop()

# ── 연결 요소 DFS ──
def dfs(node, visited, graph):
    visited[node] = True
    for nxt in graph[node]:
        if not visited[nxt]:
            dfs(nxt, visited, graph)

count = 0
for i in range(1, n+1):
    if not visited[i]:
        dfs(i, visited, graph)
        count += 1`,
    tags: ["백트래킹", "순열", "조합", "연결요소"],
  },

  // ───────────────────────── 최단 경로 ─────────────────────────
  {
    id: "a3",
    title: "다익스트라",
    category: "최단 경로",
    summary: "가중치 그래프 단일 출발 최단 경로. 음수 간선 불가",
    conditions: [
      "dist 배열 INF 초기화, 시작 노드만 0",
      "if cost > dist[node]: continue — 이미 처리된 경로 skip 필수",
      "음수 가중치 있으면 벨만-포드 알고리즘 사용",
      "(cost, node) 튜플 순서로 heapq에 삽입",
    ],
    template: `import heapq

INF = float('inf')

def dijkstra(graph, start, n):
    dist = [INF] * (n + 1)
    dist[start] = 0
    heap = [(0, start)]   # (비용, 노드)

    while heap:
        cost, node = heapq.heappop(heap)

        if cost > dist[node]:   # 이미 더 짧은 경로 존재
            continue

        for nxt, weight in graph[node]:
            new_cost = cost + weight
            if new_cost < dist[nxt]:
                dist[nxt] = new_cost
                heapq.heappush(heap, (new_cost, nxt))

    return dist

# ── 그래프 구성 ──
graph = [[] for _ in range(n + 1)]
for u, v, w in edges:
    graph[u].append((v, w))   # 유방향
    graph[v].append((u, w))   # 무방향은 양쪽

dist = dijkstra(graph, 1, n)
print(dist[target])   # 1→target 최단거리`,
    tags: ["최단경로", "가중치그래프", "heapq"],
  },

  // ───────────────────────── 동적 프로그래밍 ─────────────────────────
  {
    id: "a4",
    title: "DP — 0/1 배낭 & 무한 배낭",
    category: "동적 프로그래밍",
    summary: "무게 제한 내 최대 가치. 역방향 vs 정방향 순회가 핵심",
    conditions: [
      "0/1 배낭 (한 번만): W → weight 역방향 순회",
      "무한 배낭 (중복 허용): weight → W 정방향 순회",
      "dp[w] = 무게 w까지 담을 때 최대 가치",
      "역/정방향 혼동이 가장 흔한 실수",
    ],
    template: `# ── 0/1 배낭 (각 물건 한 번씩) ──
def knapsack_01(items, W):
    dp = [0] * (W + 1)
    for weight, value in items:
        for w in range(W, weight - 1, -1):  # ← 역방향 필수
            dp[w] = max(dp[w], dp[w - weight] + value)
    return dp[W]

# ── 무한 배낭 (중복 사용 가능) ──
def knapsack_unbounded(items, W):
    dp = [0] * (W + 1)
    for w in range(1, W + 1):
        for weight, value in items:
            if w >= weight:
                dp[w] = max(dp[w], dp[w - weight] + value)  # → 정방향
    return dp[W]

# ── 동전 최소 개수 (무한 배낭 변형) ──
def coin_change(coins, amount):
    dp = [float('inf')] * (amount + 1)
    dp[0] = 0
    for coin in coins:
        for a in range(coin, amount + 1):
            dp[a] = min(dp[a], dp[a - coin] + 1)
    return dp[amount] if dp[amount] != float('inf') else -1`,
    tags: ["배낭문제", "동전거스름돈"],
  },
  {
    id: "a5",
    title: "LIS & LCS",
    category: "동적 프로그래밍",
    summary: "최장 증가 부분수열 / 최장 공통 부분수열",
    conditions: [
      "LIS O(N²): dp[i] = i번째 원소로 끝나는 LIS 길이",
      "LIS O(N log N): bisect로 tails 배열 유지",
      "LCS O(NM): s1[i-1]==s2[j-1]이면 dp[i-1][j-1]+1, 아니면 max(위, 왼)",
      "LIS tails 배열은 실제 수열이 아닌 최적화 배열임에 주의",
    ],
    template: `import bisect

# ── LIS O(N²) ──
def lis_n2(arr):
    n = len(arr)
    dp = [1] * n
    for i in range(1, n):
        for j in range(i):
            if arr[j] < arr[i]:
                dp[i] = max(dp[i], dp[j] + 1)
    return max(dp)

# ── LIS O(N log N) ──
def lis_nlogn(arr):
    tails = []
    for x in arr:
        idx = bisect.bisect_left(tails, x)
        if idx == len(tails):
            tails.append(x)
        else:
            tails[idx] = x
    return len(tails)

# ── LCS ──
def lcs(s1, s2):
    m, n = len(s1), len(s2)
    dp = [[0]*(n+1) for _ in range(m+1)]
    for i in range(1, m+1):
        for j in range(1, n+1):
            if s1[i-1] == s2[j-1]:
                dp[i][j] = dp[i-1][j-1] + 1
            else:
                dp[i][j] = max(dp[i-1][j], dp[i][j-1])
    return dp[m][n]`,
    tags: ["LIS", "LCS", "부분수열"],
  },

  // ───────────────────────── 자료구조 ─────────────────────────
  {
    id: "a6",
    title: "Union-Find",
    category: "자료구조",
    summary: "사이클 감지 + MST(크루스칼). path compression 필수",
    conditions: [
      "parent[i] = i 로 초기화",
      "find: 재귀 path compression 필수 (없으면 TLE)",
      "union 반환 False = 이미 같은 집합 = 사이클",
      "크루스칼: 간선 비용 정렬 후 union 성공한 것만 MST에 추가",
    ],
    template: `class UnionFind:
    def __init__(self, n):
        self.parent = list(range(n))

    def find(self, x):
        if self.parent[x] != x:
            self.parent[x] = self.find(self.parent[x])  # path compression
        return self.parent[x]

    def union(self, a, b):
        a, b = self.find(a), self.find(b)
        if a == b:
            return False   # 사이클!
        self.parent[b] = a
        return True

# ── 크루스칼 MST ──
def kruskal(n, edges):
    edges.sort(key=lambda e: e[2])
    uf = UnionFind(n)
    total = 0
    for u, v, w in edges:
        if uf.union(u, v):
            total += w
    return total

# ── 사이클 감지 ──
uf = UnionFind(n)
for u, v in edges:
    if not uf.union(u, v):
        print("사이클 발견")`,
    tags: ["사이클감지", "MST", "크루스칼"],
  },
  {
    id: "a7",
    title: "단조 스택",
    category: "자료구조",
    summary: "다음·이전 더 큰/작은 원소 O(N). 히스토그램 최대 넓이",
    conditions: [
      "스택에 인덱스를 저장 (나중에 거리 계산 용이)",
      "단조 감소 스택: 더 큰 값 들어오면 pop → '다음 큰 원소' 찾기",
      "단조 증가 스택: 더 작은 값 들어오면 pop → '다음 작은 원소' 찾기",
      "히스토그램: pop할 때 (현재 인덱스 - 스택 최상단 인덱스) * 높이",
    ],
    template: `# ── 다음 더 큰 원소 ──
def next_greater(arr):
    n = len(arr)
    result = [-1] * n
    stack = []  # 인덱스 저장

    for i in range(n):
        while stack and arr[stack[-1]] < arr[i]:
            result[stack.pop()] = arr[i]
        stack.append(i)
    return result

# ── 이전 더 작은 원소 ──
def prev_smaller(arr):
    n = len(arr)
    result = [-1] * n
    stack = []

    for i in range(n):
        while stack and arr[stack[-1]] >= arr[i]:
            stack.pop()
        if stack:
            result[i] = stack[-1]
        stack.append(i)
    return result

# ── 히스토그램 최대 직사각형 ──
def largest_rect(heights):
    stack = []
    max_area = 0
    for i, h in enumerate(heights):
        start = i
        while stack and stack[-1][1] > h:
            idx, height = stack.pop()
            max_area = max(max_area, height * (i - idx))
            start = idx
        stack.append((start, h))
    for idx, height in stack:
        max_area = max(max_area, height * (len(heights) - idx))
    return max_area`,
    tags: ["단조스택", "히스토그램", "다음큰원소"],
  },

  // ───────────────────────── 탐색·정렬 ─────────────────────────
  {
    id: "a8",
    title: "이진 탐색 & 파라메트릭 서치",
    category: "탐색·정렬",
    summary: "O(log N) 탐색. '최솟값 최대화' / '최댓값 최소화' 패턴",
    conditions: [
      "is_possible(mid) 함수로 결정 문제(True/False)로 변환",
      "최솟값 최대화: 가능 → answer=mid, left=mid+1",
      "최댓값 최소화: 가능 → answer=mid, right=mid-1",
      "left <= right 조건. mid = (left+right)//2",
    ],
    template: `# ── 기본 이진 탐색 ──
def binary_search(arr, target):
    left, right = 0, len(arr) - 1
    while left <= right:
        mid = (left + right) // 2
        if arr[mid] == target: return mid
        elif arr[mid] < target: left = mid + 1
        else: right = mid - 1
    return -1

# ── 파라메트릭 서치 (최솟값 최대화) ──
# 예: 징검다리 — 건널 수 있는 최대 인원
def solve(stones, k):
    def can_cross(mid):
        cnt = 0
        for s in stones:
            cnt = cnt + 1 if s - mid <= 0 else 0
            if cnt >= k: return False
        return True

    left, right = 1, max(stones)
    answer = 1
    while left <= right:
        mid = (left + right) // 2
        if can_cross(mid):
            answer = mid
            left = mid + 1   # 더 큰 값 탐색
        else:
            right = mid - 1
    return answer

# ── 최댓값 최소화 (나무 자르기) ──
def cut_tree(trees, target):
    def get_wood(h):
        return sum(max(0, t - h) for t in trees)
    left, right = 0, max(trees)
    answer = 0
    while left <= right:
        mid = (left + right) // 2
        if get_wood(mid) >= target:
            answer = mid
            left = mid + 1
        else:
            right = mid - 1
    return answer`,
    tags: ["이진탐색", "파라메트릭서치", "결정문제"],
  },
  {
    id: "a9",
    title: "투 포인터",
    category: "탐색·정렬",
    summary: "정렬 배열 O(N) 합 탐색. 슬라이딩 윈도우와 다름",
    conditions: [
      "양 끝에서 좁혀나감 (슬라이딩 윈도우는 구간을 오른쪽으로 이동)",
      "합 < target: left++, 합 > target: right--, 합 == target: 기록",
      "정렬된 배열에서만 의미 있음",
      "슬라이딩 윈도우: 합 >= target인 최소 길이 구할 때는 포인터 둘 다 오른쪽",
    ],
    template: `# ── 두 수의 합 ──
def two_sum(arr, target):
    arr.sort()
    left, right = 0, len(arr) - 1
    result = []
    while left < right:
        s = arr[left] + arr[right]
        if s == target:
            result.append((arr[left], arr[right]))
            left += 1; right -= 1
        elif s < target: left += 1
        else: right -= 1
    return result

# ── 합 >= target인 최소 길이 부분배열 ──
def min_subarray(arr, target):
    left = 0
    total = 0
    min_len = float('inf')
    for right in range(len(arr)):
        total += arr[right]
        while total >= target:
            min_len = min(min_len, right - left + 1)
            total -= arr[left]
            left += 1
    return min_len if min_len != float('inf') else 0

# ── 세 수의 합 ──
def three_sum(arr, target):
    arr.sort(); result = []
    for i in range(len(arr) - 2):
        if i > 0 and arr[i] == arr[i-1]: continue
        l, r = i+1, len(arr)-1
        while l < r:
            s = arr[i]+arr[l]+arr[r]
            if s == target:
                result.append((arr[i],arr[l],arr[r]))
                l += 1; r -= 1
            elif s < target: l += 1
            else: r -= 1
    return result`,
    tags: ["투포인터", "부분합", "O(N)"],
  },

  // ───────────────────────── 위상 정렬 ─────────────────────────
  {
    id: "a10",
    title: "위상 정렬",
    category: "위상 정렬",
    summary: "DAG 의존성 순서 결정. 선수과목·작업 순서 문제",
    conditions: [
      "진입차수(in-degree) 기반 BFS (Kahn 알고리즘)",
      "진입차수 0인 노드부터 큐에 넣어 시작",
      "결과 길이 != n이면 순환 그래프 (위상 정렬 불가)",
      "DAG(방향 비순환 그래프)에서만 사용 가능",
    ],
    template: `from collections import deque, defaultdict

def topological_sort(n, edges):
    """
    edges: [(from, to), ...]
    반환: 정렬 순서 (순환 그래프면 빈 리스트)
    """
    in_degree = [0] * n
    graph = defaultdict(list)

    for u, v in edges:
        graph[u].append(v)
        in_degree[v] += 1

    queue = deque(i for i in range(n) if in_degree[i] == 0)
    result = []

    while queue:
        node = queue.popleft()
        result.append(node)
        for nxt in graph[node]:
            in_degree[nxt] -= 1
            if in_degree[nxt] == 0:
                queue.append(nxt)

    return result if len(result) == n else []  # 순환 감지

# ── 선수과목 문제 ──
def can_finish(num_courses, prerequisites):
    graph = defaultdict(list)
    in_degree = [0] * num_courses
    for course, pre in prerequisites:
        graph[pre].append(course)
        in_degree[course] += 1
    queue = deque(i for i in range(num_courses) if in_degree[i] == 0)
    count = 0
    while queue:
        node = queue.popleft(); count += 1
        for nxt in graph[node]:
            in_degree[nxt] -= 1
            if in_degree[nxt] == 0: queue.append(nxt)
    return count == num_courses`,
    tags: ["DAG", "선수과목", "의존성"],
  },
]
