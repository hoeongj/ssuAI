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
    <main className="mx-auto flex min-h-screen w-full max-w-6xl flex-col gap-8 px-4 py-6 sm:px-6 lg:px-8">
      <header className="flex items-center justify-between gap-4 border-b border-border pb-5">
        <div className="min-w-0">
          <p className="text-sm font-medium text-muted-foreground">Soongsil University</p>
          <h1 className="mt-2 truncate text-3xl font-semibold tracking-normal text-foreground">ssuAI</h1>
        </div>
        <div className="flex items-center gap-4">
          <UserGreeting />
          <Link href="/chat" className={buttonVariants({ variant: "default" })}>
            <MessageCircle className="h-4 w-4" aria-hidden="true" />
            Chat
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
