import { ImageResponse } from "next/og";

export const runtime = "nodejs";
export const alt = "bytecode.news";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default async function OgImage() {
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          backgroundColor: "#1a1917",
          fontFamily: "Georgia, serif",
          position: "relative",
        }}
      >
        {/* Top amber accent bar */}
        <div
          style={{
            position: "absolute",
            top: 0,
            left: 0,
            right: 0,
            height: 6,
            backgroundColor: "#c8912e",
          }}
        />

        {/* Thin amber rule above title */}
        <div
          style={{
            width: 600,
            height: 2,
            backgroundColor: "#c8912e",
            marginBottom: 40,
          }}
        />

        {/* Site name */}
        <div
          style={{
            fontSize: 96,
            letterSpacing: "-0.04em",
            lineHeight: 1,
            color: "#e8e2d6",
            display: "flex",
            alignItems: "center",
            gap: 20,
          }}
        >
          <span>bytecode</span>
          <span style={{ color: "#c8912e", fontSize: 72 }}>.</span>
          <span>news</span>
        </div>

        {/* Tagline */}
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 20,
            marginTop: 32,
          }}
        >
          <div
            style={{
              width: 60,
              height: 1,
              backgroundColor: "#c8912e",
              opacity: 0.5,
            }}
          />
          <span
            style={{
              fontSize: 16,
              letterSpacing: "0.2em",
              textTransform: "uppercase",
              color: "#a09882",
              fontFamily: "monospace",
            }}
          >
            Programming News & Technical Writing
          </span>
          <div
            style={{
              width: 60,
              height: 1,
              backgroundColor: "#c8912e",
              opacity: 0.5,
            }}
          />
        </div>

        {/* Thick amber rule below */}
        <div
          style={{
            width: 600,
            height: 3,
            backgroundColor: "#c8912e",
            marginTop: 40,
          }}
        />
        {/* Thin amber rule below */}
        <div
          style={{
            width: 600,
            height: 1,
            backgroundColor: "#c8912e",
            opacity: 0.4,
            marginTop: 4,
          }}
        />

        {/* Bottom accent bar */}
        <div
          style={{
            position: "absolute",
            bottom: 0,
            left: 0,
            right: 0,
            height: 6,
            backgroundColor: "#c8912e",
          }}
        />
      </div>
    ),
    { ...size },
  );
}
