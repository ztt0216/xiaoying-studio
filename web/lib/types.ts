export type TaskStatus = "PENDING" | "PROCESSING" | "SUCCEEDED" | "FAILED";
export type TaskStage = "ASSET_CHECK" | "GENERATE" | "FINALIZE";

export const STAGES: { key: TaskStage; label: string }[] = [
  { key: "ASSET_CHECK", label: "素材检查" },
  { key: "GENERATE", label: "模拟生成" },
  { key: "FINALIZE", label: "结果整理" },
];

export function stageLabel(stage: string | null | undefined): string {
  return STAGES.find((s) => s.key === stage)?.label ?? "等待处理";
}

export function isTerminal(status: TaskStatus): boolean {
  return status === "SUCCEEDED" || status === "FAILED";
}

export interface User {
  id: number;
  nickname: string;
  createdAt: string;
}

export interface Template {
  id: string;
  title: string;
  category: string;
  coverUrl: string;
  description: string;
  requiredImageCount: number;
  acceptedMimeTypes: string[];
  maxFileSizeBytes: number;
}

export interface TemplateList {
  categories: string[];
  items: Template[];
}

export interface Asset {
  id: number;
  url: string;
  mimeType: string;
  sizeBytes: number;
  originalName: string;
}

export interface TaskStep {
  stage: TaskStage;
  status: "RUNNING" | "SUCCEEDED" | "FAILED";
  message?: string;
  startedAt: string;
  finishedAt?: string;
}

export interface Task {
  id: number;
  templateId: string;
  templateTitle: string;
  templateCoverUrl: string;
  status: TaskStatus;
  stage?: TaskStage | null;
  failedStage?: TaskStage | null;
  errorMessage?: string | null;
  resultUrl?: string | null;
  inputUrls: string[];
  steps?: TaskStep[];
  createdAt: string;
  updatedAt: string;
  startedAt?: string;
  finishedAt?: string;
}

export interface SimulationConfig {
  stepDelayMs: number;
  failStage: "NONE" | TaskStage;
  stages: TaskStage[];
}
