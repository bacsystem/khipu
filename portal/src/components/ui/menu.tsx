"use client";

import * as React from "react";
import { Menu as MenuPrimitive } from "@base-ui/react/menu";
import { CheckIcon } from "lucide-react";
import { cn } from "@/lib/utils";

const Menu = MenuPrimitive.Root;
const MenuTrigger = MenuPrimitive.Trigger;
const MenuGroup = MenuPrimitive.Group;
const MenuRadioGroup = MenuPrimitive.RadioGroup;

function MenuContent({
  className,
  side = "top",
  sideOffset = 6,
  align = "start",
  alignOffset = 0,
  ...props
}: MenuPrimitive.Popup.Props & Pick<MenuPrimitive.Positioner.Props, "side" | "sideOffset" | "align" | "alignOffset">) {
  return (
    <MenuPrimitive.Portal>
      <MenuPrimitive.Positioner side={side} sideOffset={sideOffset} align={align} alignOffset={alignOffset} className="isolate z-50">
        <MenuPrimitive.Popup
          data-slot="menu-content"
          className={cn(
            "min-w-56 rounded-lg bg-popover p-1 text-popover-foreground shadow-md ring-1 ring-foreground/10 outline-none",
            "origin-(--transform-origin) transition-[opacity,transform] duration-150 data-ending-style:scale-95 data-ending-style:opacity-0 data-starting-style:scale-95 data-starting-style:opacity-0",
            className,
          )}
          {...props}
        />
      </MenuPrimitive.Positioner>
    </MenuPrimitive.Portal>
  );
}

const ITEM =
  "relative flex w-full cursor-default items-center gap-2 rounded-md px-2 py-1.5 text-[12px] leading-tight font-medium text-foreground outline-none select-none data-highlighted:bg-accent/70 data-highlighted:text-accent-foreground data-disabled:pointer-events-none data-disabled:opacity-50 [&_svg]:size-3.5 [&_svg]:shrink-0 [&_svg]:text-muted-foreground data-highlighted:[&_svg]:text-accent-foreground";

function MenuItem({ className, variant = "default", ...props }: MenuPrimitive.Item.Props & { variant?: "default" | "destructive" }) {
  return (
    <MenuPrimitive.Item
      data-slot="menu-item"
      className={cn(
        ITEM,
        variant === "destructive" &&
          "text-destructive data-highlighted:bg-destructive/10 data-highlighted:text-destructive [&_svg]:text-destructive data-highlighted:[&_svg]:text-destructive",
        className,
      )}
      {...props}
    />
  );
}

function MenuLinkItem({ className, ...props }: MenuPrimitive.LinkItem.Props) {
  return <MenuPrimitive.LinkItem data-slot="menu-link-item" className={cn(ITEM, className)} {...props} />;
}

function MenuRadioItem({ className, children, ...props }: MenuPrimitive.RadioItem.Props) {
  return (
    <MenuPrimitive.RadioItem data-slot="menu-radio-item" className={cn(ITEM, "pr-8", className)} {...props}>
      {children}
      <MenuPrimitive.RadioItemIndicator className="absolute right-2 flex items-center">
        <CheckIcon className="size-3.5 text-foreground" />
      </MenuPrimitive.RadioItemIndicator>
    </MenuPrimitive.RadioItem>
  );
}

function MenuGroupLabel({ className, ...props }: MenuPrimitive.GroupLabel.Props) {
  return (
    <MenuPrimitive.GroupLabel
      data-slot="menu-group-label"
      className={cn("px-2 pt-1.5 pb-1 text-[10px] font-medium tracking-wider text-muted-foreground uppercase", className)}
      {...props}
    />
  );
}

function MenuSeparator({ className, ...props }: MenuPrimitive.Separator.Props) {
  return <MenuPrimitive.Separator data-slot="menu-separator" className={cn("-mx-1 my-1 h-px bg-border/70", className)} {...props} />;
}

export { Menu, MenuTrigger, MenuContent, MenuItem, MenuLinkItem, MenuGroup, MenuGroupLabel, MenuRadioGroup, MenuRadioItem, MenuSeparator };
