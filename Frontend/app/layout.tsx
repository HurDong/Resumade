import type { Metadata } from 'next'
import { Inter } from 'next/font/google'
import { Analytics } from '@vercel/analytics/next'
import { GlobalCommandPalette } from '@/components/global-command-palette'
import { ThemeProvider } from '@/components/theme-provider'
import { TooltipProvider } from '@/components/ui/tooltip'
import { Toaster } from 'sonner'
import './globals.css'

const inter = Inter({ 
  subsets: ["latin"],
  variable: '--font-inter'
});

export const metadata: Metadata = {
  title: 'RESUMADE | AI 자소서 비서',
  description: '구직자를 위한 개인화된 자기소개서 작성 및 채용 파이프라인 관리 SaaS',
  generator: 'v0.app',
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="ko" suppressHydrationWarning>
      <body className={`${inter.variable} font-sans antialiased`}>
        <ThemeProvider
          attribute="class"
          defaultTheme="light"
          enableSystem
          disableTransitionOnChange
        >
          <TooltipProvider delayDuration={300}>
            {children}
            <GlobalCommandPalette />
          </TooltipProvider>
          <Toaster position="top-center" richColors />
        </ThemeProvider>
        <Analytics />
      </body>
    </html>
  )
}
