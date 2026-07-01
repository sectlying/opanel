"use client";

import type { OidcConfigResponse } from "@/lib/types";
import { useEffect, useState } from "react";
import { ShieldCheck, Trash2, Plus } from "lucide-react";
import { toast } from "sonner";
import { SubPage } from "../sub-page";
import { $ } from "@/lib/i18n";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { sendGetRequest, sendPostRequest, sendDeleteRequest, toastError } from "@/lib/api";
import { ConfigItem, ConfigSection } from "@/components/config-item";
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty";
import { emitter } from "@/lib/emitter";

export default function OIDCConfiguration() {
  const [enabled, setEnabled] = useState<boolean | null>(null);
  const [allowedUserIds, setAllowedUserIds] = useState<string[]>([]);
  const [newUserId, setNewUserId] = useState("");

  const fetchOidcStatus = async () => {
    try {
      const res = await sendGetRequest<OidcConfigResponse>("/api/auth/oidc/config", false);
      setEnabled(res.enabled);
      if(res.enabled) {
        const { allowedUserIds: ids } = await sendGetRequest<{ allowedUserIds: string[] }>("/api/auth/oidc/allowed-users");
        setAllowedUserIds(ids ?? []);
      }
    } catch (e: any) {
        toastError(e, $("oidc.fetch.error"), [
        [401, $("common.error.401")],
        [500, $("common.error.500")]
      ]);
      setEnabled(false);
    } finally {
      emitter.emit("loading-done");
    }
  };

  const handleAdd = async () => {
    const trimmed = newUserId.trim();
    if(!trimmed) return;

    try {
      await sendPostRequest("/api/auth/oidc/allowed-users", { userId: trimmed });
      setNewUserId("");
      toast.success($("oidc.add.success"));
      await fetchOidcStatus();
    } catch (e: any) {
      toastError(e, $("oidc.add.error"), [
        [400, $("common.error.400")],
        [401, $("common.error.401")],
        [500, $("common.error.500")]
      ]);
    }
  };

  const handleRemove = async (userId: string) => {
    try {
      await sendDeleteRequest("/api/auth/oidc/allowed-users", { userId });
      toast.success($("oidc.remove.success"));
      await fetchOidcStatus();
    } catch (e: any) {
      toastError(e, $("oidc.remove.error"), [
        [400, $("common.error.400")],
        [401, $("common.error.401")],
        [500, $("common.error.500")]
      ]);
    }
  };

  useEffect(() => {
    fetchOidcStatus();
  }, []);

  return (
    <SubPage
      title="OIDC"
      subTitle="OIDC"
      description={$("oidc.description")}
      category={$("nav.settings")}
      icon={<ShieldCheck />}
      pageClassName="min-xl:px-64!">
      {enabled === false ? (
        <Empty>
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <ShieldCheck />
            </EmptyMedia>
            <EmptyTitle>{$("oidc.not-enabled")}</EmptyTitle>
          </EmptyHeader>
        </Empty>
      ) : (
        <ConfigSection>
          <ConfigItem
            name={$("oidc.item.allowed-users")}
            description={$("oidc.item.allowed-users.description")}>
            <div className="w-full flex gap-2">
              <Input
                className="flex-1"
                placeholder={$("oidc.item.allowed-users.placeholder")}
                value={newUserId}
                onChange={(e) => setNewUserId(e.target.value)}
                onKeyDown={(e) => { if(e.key === "Enter") handleAdd(); }} />
              <Button
                className="cursor-pointer"
                size="sm"
                disabled={!newUserId.trim()}
                onClick={handleAdd}>
                <Plus />
                {$("oidc.add")}
              </Button>
            </div>
          </ConfigItem>
          {allowedUserIds.length === 0 ? (
            <div className="px-4 py-3 text-sm text-muted-foreground">
              {$("oidc.item.allowed-users.empty")}
            </div>
          ) : (
            allowedUserIds.map((userId) => (
              <div key={userId} className="flex items-center gap-2 px-4 py-2 border-b last:border-b-0">
                <span className="text-sm font-mono mr-auto">{userId}</span>
                <Button
                  variant="ghost"
                  size="icon"
                  className="cursor-pointer text-destructive hover:text-destructive"
                  onClick={() => handleRemove(userId)}>
                  <Trash2 size={16} />
                </Button>
              </div>
            ))
          )}
        </ConfigSection>
      )}
    </SubPage>
  );
}
