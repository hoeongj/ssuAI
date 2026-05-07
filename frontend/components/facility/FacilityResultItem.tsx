import { Clock, MapPin, Phone } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import type { CampusFacility } from "@/lib/api/types";

interface FacilityResultItemProps {
  facility: CampusFacility;
}

function joinContact(facility: CampusFacility) {
  const contacts = [facility.phone, facility.extension ? `내선 ${facility.extension}` : ""].filter(Boolean);
  return contacts.length > 0 ? contacts.join(" · ") : "연락처 없음";
}

export function FacilityResultItem({ facility }: FacilityResultItemProps) {
  return (
    <article className="rounded-md border border-border p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 space-y-1">
          <h4 className="text-sm font-semibold text-foreground">{facility.name}</h4>
          <p className="text-xs text-muted-foreground">{facility.aliases.join(", ")}</p>
        </div>
        <Badge variant="secondary">{facility.categoryLabel}</Badge>
      </div>

      <dl className="mt-4 space-y-2 text-sm">
        <div className="flex gap-2">
          <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
          <dt className="sr-only">위치</dt>
          <dd className="text-foreground">{facility.location || "위치 정보 없음"}</dd>
        </div>
        <div className="flex gap-2">
          <Phone className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
          <dt className="sr-only">연락처</dt>
          <dd className="text-foreground">{joinContact(facility)}</dd>
        </div>
        <div className="flex gap-2">
          <Clock className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
          <dt className="sr-only">운영 시간</dt>
          <dd className="space-y-1 text-foreground">
            {facility.weekdayHours.length > 0 ? (
              <p>평일: {facility.weekdayHours.join(", ")}</p>
            ) : null}
            {facility.weekendHours.length > 0 ? (
              <p>주말: {facility.weekendHours.join(", ")}</p>
            ) : null}
            {facility.weekdayHours.length === 0 && facility.weekendHours.length === 0 ? (
              <p>운영 시간 정보 없음</p>
            ) : null}
          </dd>
        </div>
      </dl>

      {facility.notes.length > 0 ? (
        <ul className="mt-3 list-disc space-y-1 pl-5 text-xs text-muted-foreground">
          {facility.notes.map((note) => (
            <li key={note}>{note}</li>
          ))}
        </ul>
      ) : null}
    </article>
  );
}
