"use client";

import { type FormEvent, type PropsWithChildren, useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { $ } from "@/lib/i18n";

export function CoordDialog({
  children,
  asChild,
  onTeleport,
  getInitialCoord
}: PropsWithChildren<{
  asChild?: boolean
  onTeleport: (coord: { x: number, z: number }) => void
  getInitialCoord: () => { x: number, z: number } | null
}>) {
  const [open, setOpen] = useState(false);
  const [x, setX] = useState("");
  const [z, setZ] = useState("");

  const handleOpenChange = (nextOpen: boolean) => {
    setOpen(nextOpen);
    if(!nextOpen) return;

    const coord = getInitialCoord();
    if(!coord) return;

    setX(coord.x.toFixed(0));
    setZ(coord.z.toFixed(0));
  };

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();

    const nextX = Number(x);
    const nextZ = Number(z);
    if(!Number.isFinite(nextX) || !Number.isFinite(nextZ)) return;

    onTeleport({ x: nextX, z: nextZ });
    setOpen(false);
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild={asChild}>{children}</DialogTrigger>
      <DialogContent>
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>{$("map.coord-dialog.title")}</DialogTitle>
            <DialogDescription>
              {$("map.coord-dialog.description")}
            </DialogDescription>
          </DialogHeader>
          <FieldGroup className="flex-row gap-4 my-4">
            <Field>
              <FieldLabel>{$("map.coord-dialog.x-coordinate")}</FieldLabel>
              <Input
                type="number"
                value={x}
                onChange={(e) => setX(e.target.value)}
                onFocus={(e) => e.target.select()}
                required/>
            </Field>
            <Field>
              <FieldLabel>{$("map.coord-dialog.z-coordinate")}</FieldLabel>
              <Input
                type="number"
                value={z}
                onChange={(e) => setZ(e.target.value)}
                onFocus={(e) => e.target.select()}
                required/>
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button type="submit">
              {$("map.coord-dialog.teleport")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
