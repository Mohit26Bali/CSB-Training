import type { Program } from './api/types'

/** Mirrors the server's formula, for the live preview only. The server works out the score that is saved. */
export function previewScore(gpa: number, income: number, program: Program): number {
  const academics = (gpa / 10) * 60
  const need = income >= program.maxFamilyIncome ? 0 : (1 - income / program.maxFamilyIncome) * 40
  return Math.round(Math.min(100, academics + need))
}

export function previewMeetsCriteria(gpa: number, income: number, program: Program): boolean {
  return gpa >= program.minGpa && income <= program.maxFamilyIncome
}

export function daysLeft(deadline: string): number {
  const [y, m, d] = deadline.split('-').map(Number)
  const end = new Date(y, m - 1, d)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Math.round((end.getTime() - today.getTime()) / 86_400_000)
}
