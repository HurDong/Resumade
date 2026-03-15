/**
 * Utility to fetch company logos based on company name.
 * Uses Clearbit and Google Favicon APIs as fallbacks.
 */
const COMPANY_DOMAINS: Record<string, string> = {
  "삼성전자": "samsung.com",
  "삼성": "samsung.com",
  "현대자동차": "hyundai.com",
  "현대": "hyundai.com",
  "카카오": "kakao.com",
  "네이버": "naver.com",
  "naver": "naver.com",
  "토스": "toss.im",
  "toss": "toss.im",
  "라인": "line.me",
  "line": "line.me",
  "쿠팡": "coupang.com",
  "coupang": "coupang.com",
  "배달의민족": "woowahan.com",
  "우아한형제들": "woowahan.com",
  "당근마켓": "daangn.com",
  "당근": "daangn.com",
  "ibk기업은행": "ibk.co.kr",
  "기업은행": "ibk.co.kr",
  "신한은행": "shinhan.com",
  "국민은행": "kbstar.com",
  "sk하이닉스": "skhynix.com",
  "lg전자": "lge.co.kr",
  "롯데": "lotte.co.kr",
  "롯데이노베이트": "lotteinnovate.com",
}

/**
 * Utility to fetch company logos based on company name.
 * Uses Clearbit and Google Favicon APIs as fallbacks.
 */
export const getCompanyLogo = (companyName: string): string => {
  if (!companyName) return ""
  
  // 1. Clean the name (remove common suffixes and parenthesized text)
  let cleanName = companyName
    .replace(/\(주\)|주식회사|\(공고\)|채용|상반기|하반기/g, "")
    .split(/\s|\(/)[0] // Take first word before space or parenthesis
    .trim()
    .toLowerCase()

  // 2. Check if we have a hardcoded domain mapping
  const mappedDomain = COMPANY_DOMAINS[cleanName] || 
                      Object.entries(COMPANY_DOMAINS).find(([k]) => cleanName.includes(k))?.[1]

  if (mappedDomain) {
    return `https://logo.clearbit.com/${mappedDomain}`
  }

  // 3. Fallback to constant guessing for common English names
  if (/^[a-z]+$/i.test(cleanName)) {
    return `https://logo.clearbit.com/${cleanName}.com`
  }

  // 4. Ultimate fallback: Google Favicon API using the cleaned name as a query
  // (Note: This is less reliable for logos but better than nothing)
  return `https://www.google.com/s2/favicons?domain=${cleanName}.com&sz=128`
}
