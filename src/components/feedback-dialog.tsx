"use client";

import { useCallback, useState } from "react";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { cn } from "@/lib/utils";
import { APP_VERSION } from "@/lib/version";
import { MessageSquareWarning, Loader2, CheckCircle2, ExternalLink, ShieldCheck } from "lucide-react";

const TYPES = [
  { value: "missed", label: "漏拦截（广告没被秒跳）" },
  { value: "shake_leak", label: "摇一摇仍被跳转" },
  { value: "false_block", label: "误拦截（正常操作被拦）" },
  { value: "crash", label: "闪退 / 卡顿" },
  { value: "suggestion", label: "功能建议" },
  { value: "other", label: "其他" },
];

/**
 * 问题反馈弹窗 —— 内置加密通道直达 GitHub Issues
 * 用户零配置：无令牌输入、无仓库选择，令牌仅存于服务端（混淆存储）。
 */
export function FeedbackDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (v: boolean) => void }) {
  const [type, setType] = useState("missed");
  const [appName, setAppName] = useState("");
  const [desc, setDesc] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<{ ok: boolean; url?: string; error?: string } | null>(null);

  const reset = useCallback(() => {
    setType("missed");
    setAppName("");
    setDesc("");
    setResult(null);
    setSubmitting(false);
  }, []);

  const submit = useCallback(async () => {
    if (desc.trim().length < 5) {
      setResult({ ok: false, error: "请把问题描述得更具体一点（至少 5 个字）" });
      return;
    }
    setSubmitting(true);
    setResult(null);
    try {
      const r = await fetch("/api/feedback", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          type,
          desc,
          appName,
          context: `来源：演练场内置反馈弹窗 · 引擎版本 v${APP_VERSION}`,
        }),
      });
      const d = await r.json();
      if (r.ok && d.ok) {
        setResult({ ok: true, url: d.url });
      } else {
        setResult({ ok: false, error: d.error ?? `提交失败（HTTP ${r.status}）` });
      }
    } catch {
      setResult({ ok: false, error: "网络异常，请稍后重试" });
    }
    setSubmitting(false);
  }, [type, desc, appName]);

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        onOpenChange(v);
        if (!v) setTimeout(reset, 200);
      }}
    >
      <DialogContent className="max-w-md rounded-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <MessageSquareWarning className="h-5 w-5 text-violet-600" /> 问题反馈
          </DialogTitle>
          <DialogDescription className="text-left text-[11px] leading-relaxed">
            <span className="flex items-center gap-1 font-semibold text-emerald-600">
              <ShieldCheck className="h-3.5 w-3.5" /> 内置加密通道 · 无需任何配置
            </span>
            反馈将自动创建为开发者 GitHub Issues，令牌已内置于服务端，不会出现在你的浏览器中。
          </DialogDescription>
        </DialogHeader>

        {result?.ok ? (
          <div className="flex flex-col items-center gap-3 py-6 text-center">
            <span className="flex h-14 w-14 items-center justify-center rounded-full bg-emerald-50 ring-1 ring-emerald-200">
              <CheckCircle2 className="h-7 w-7 text-emerald-600" />
            </span>
            <div className="text-sm font-bold text-zinc-900">反馈已直达 GitHub Issues</div>
            {result.url && (
              <a href={result.url} target="_blank" rel="noreferrer" className="flex items-center gap-1 text-xs font-bold text-violet-600 hover:underline">
                查看 Issue <ExternalLink className="h-3 w-3" />
              </a>
            )}
            <Button size="sm" variant="outline" className="mt-1 h-8 rounded-lg" onClick={() => onOpenChange(false)}>
              完成
            </Button>
          </div>
        ) : (
          <div className="flex flex-col gap-3.5">
            <div className="grid grid-cols-2 gap-3">
              <div className="col-span-2">
                <label className="mb-1.5 block text-xs font-bold text-zinc-700">问题类型</label>
                <Select value={type} onValueChange={setType}>
                  <SelectTrigger className="h-9 rounded-lg text-xs" data-testid="feedback-type">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {TYPES.map((t) => (
                      <SelectItem key={t.value} value={t.value} className="text-xs">
                        {t.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="col-span-2">
                <label className="mb-1.5 block text-xs font-bold text-zinc-700">
                  涉及应用 <span className="font-normal text-zinc-400">（选填）</span>
                </label>
                <Input
                  data-testid="feedback-app"
                  value={appName}
                  onChange={(e) => setAppName(e.target.value)}
                  placeholder="如：某购物 App / 某银行 App"
                  className="h-9 rounded-lg text-xs"
                  maxLength={40}
                />
              </div>
            </div>
            <div>
              <label className="mb-1.5 block text-xs font-bold text-zinc-700">问题描述</label>
              <Textarea
                data-testid="feedback-desc"
                value={desc}
                onChange={(e) => setDesc(e.target.value)}
                placeholder="什么应用、什么广告形态、引擎的表现如何？越具体越有利于定位问题"
                className="min-h-[88px] rounded-lg text-xs"
                maxLength={2000}
              />
            </div>
            {result && !result.ok && (
              <p className="rounded-lg bg-rose-50 px-3 py-2 text-[11px] font-medium text-rose-600" data-testid="feedback-error">
                {result.error}
              </p>
            )}
            <DialogFooter className="flex-row gap-2">
              <Button variant="outline" size="sm" className="h-9 flex-1 rounded-lg" onClick={() => onOpenChange(false)}>
                取消
              </Button>
              <Button
                size="sm"
                data-testid="feedback-submit"
                className="h-9 flex-1 rounded-lg bg-violet-600 hover:bg-violet-500"
                onClick={() => void submit()}
                disabled={submitting}
              >
                {submitting ? <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" /> : null}
                {submitting ? "提交中…" : "提交到 GitHub"}
              </Button>
            </DialogFooter>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
