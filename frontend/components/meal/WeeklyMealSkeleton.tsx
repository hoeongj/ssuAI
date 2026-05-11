import { Skeleton } from "@/components/ui/skeleton";

interface WeeklyMealSkeletonProps {
  dayCount?: number;
}

export function WeeklyMealSkeleton({ dayCount = 7 }: WeeklyMealSkeletonProps) {
  return (
    <div className="space-y-4">
      <div className="grid gap-2 sm:grid-cols-5">
        {Array.from({ length: dayCount }).map((_, index) => (
          <Skeleton key={index} className="h-24 w-full" />
        ))}
      </div>
      <Skeleton className="h-36 w-full" />
    </div>
  );
}
