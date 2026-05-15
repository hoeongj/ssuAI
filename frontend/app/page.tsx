import { MessageCircle } from "lucide-react";
import Link from "next/link";

import { UserGreeting } from "@/components/auth/UserGreeting";
import { DormWeeklyCard } from "@/components/dorm/DormWeeklyCard";
import { FacilitySearchCard } from "@/components/facility/FacilitySearchCard";
import { LibrarySeatCard } from "@/components/library/LibrarySeatCard";
import { TodayMealCard } from "@/components/meal/TodayMealCard";
import { WeeklyMealCard } from "@/components/meal/WeeklyMealCard";
import { buttonVariants } from "@/components/ui/button";

export default function Home() {
  return (
    <main className="mx-auto flex min-h-screen w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:gap-8 sm:px-6 lg:px-8">
      <header className="flex items-center justify-between gap-3 border-b border-border pb-5 sm:gap-4">
        <div className="min-w-0 flex-1">
          <p className="text-xs font-medium text-muted-foreground sm:text-sm">Soongsil University</p>
          <h1 className="mt-1 truncate text-2xl font-semibold tracking-normal text-foreground sm:mt-2 sm:text-3xl">
            ssuAI
          </h1>
        </div>
        <div className="flex shrink-0 items-center gap-2 sm:gap-3">
          <UserGreeting />
          <Link
            href="/chat"
            className={buttonVariants({ variant: "default", size: "sm" })}
            aria-label="Chat"
          >
            <MessageCircle className="h-4 w-4" aria-hidden="true" />
            <span className="hidden sm:inline">Chat</span>
          </Link>
        </div>
      </header>
      <section className="grid gap-4 md:grid-cols-2">
        <TodayMealCard />
        <FacilitySearchCard />
        <WeeklyMealCard />
        <DormWeeklyCard />
        <LibrarySeatCard />
      </section>
    </main>
  );
}
