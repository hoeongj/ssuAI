import { TodayMealCard } from "@/components/meal/TodayMealCard";

export default function Home() {
  return (
    <main className="mx-auto flex min-h-screen w-full max-w-6xl flex-col gap-8 px-4 py-6 sm:px-6 lg:px-8">
      <header className="border-b border-border pb-5">
        <p className="text-sm font-medium text-muted-foreground">Soongsil University</p>
        <h1 className="mt-2 text-3xl font-semibold tracking-normal text-foreground">ssuAI</h1>
      </header>
      <section className="grid gap-4 md:grid-cols-2">
        <TodayMealCard />
      </section>
    </main>
  );
}
