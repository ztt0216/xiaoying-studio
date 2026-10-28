import type { NextConfig } from "next";

// Java 后端地址。Next.js 只做同源反向代理（/api、/files、/demo），业务逻辑与持久化都在 Java。
// rewrites 在 next build 时固化，Docker 中通过构建参数 BACKEND_URL 指定。
const backend = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${backend}/api/:path*` },
      { source: "/files/:path*", destination: `${backend}/files/:path*` },
      { source: "/demo/:path*", destination: `${backend}/demo/:path*` },
    ];
  },
};

export default nextConfig;
