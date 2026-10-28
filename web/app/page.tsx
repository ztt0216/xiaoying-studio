import { Suspense } from "react";
import { TemplateBrowser } from "@/components/TemplateBrowser";
import { Loading } from "@/components/StateViews";

export default function HomePage() {
  return (
    <Suspense fallback={<Loading />}>
      <TemplateBrowser />
    </Suspense>
  );
}
