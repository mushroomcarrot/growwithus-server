"""Render standalone scientific figures from the offline validation report."""

import argparse
import json
from pathlib import Path


def render(report_path, destination, font_file=None):
    import matplotlib
    matplotlib.use("Agg")
    from matplotlib import pyplot as plt, font_manager
    report = json.loads(Path(report_path).read_text(encoding="utf-8"))
    destination = Path(destination)
    destination.mkdir(parents=True, exist_ok=True)
    font_file = Path(font_file or "C:/Windows/Fonts/malgun.ttf")
    if font_file.is_file():
        font_manager.fontManager.addfont(str(font_file))
        family = font_manager.FontProperties(fname=str(font_file)).get_name()
    else:
        families = {entry.name for entry in font_manager.fontManager.ttflist}
        family = next((name for name in ("Noto Sans CJK KR", "NanumGothic", "AppleGothic") if name in families), None)
        if family is None:
            raise ValueError("A Korean font is required. Provide --font-file with a local font path.")
    plt.rcParams.update({"font.family": family, "axes.unicode_minus": False, "font.size": 10,
                         "axes.spines.top": False, "axes.spines.right": False,
                         "svg.hashsalt": "yongin-regional-validation", "savefig.facecolor": "white"})
    colors = ["#0072B2", "#D55E00", "#009E73"]
    markers = ["o", "^", "s"]
    ratios = report["pca"]["explained_variance_ratio"]
    rows = report["regions"]

    def save(fig, name):
        fig.savefig(destination / (name + ".png"), dpi=180, bbox_inches="tight")
        fig.savefig(destination / (name + ".svg"), bbox_inches="tight", metadata={"Date": None})
        svg = destination / (name + ".svg")
        svg.write_text("\n".join(line.rstrip() for line in svg.read_text(encoding="utf-8").splitlines()) + "\n", encoding="utf-8")
        plt.close(fig)

    fig, ax = plt.subplots(figsize=(10.5, 7))
    fig.subplots_adjust(bottom=0.17, top=0.83, left=0.1, right=0.96)
    for label in range(3):
        group = [row for row in rows if row["cluster"] == label]
        ax.scatter([row["pca"][0] for row in group], [row["pca"][1] for row in group], s=85,
                   color=colors[label], marker=markers[label], edgecolors="white", linewidths=0.7,
                   label=f"군집 {label} ({len(group)}개 지역)", zorder=3)
    targets = [row for row in rows if row["cluster"] == 0]
    targets += sorted((row for row in rows if row["cluster"] != 0), key=lambda row: row["bootstrap_agreement"])[:3]
    offsets = [(8, 10), (8, 10), (8, -23), (-55, -10), (8, 16)]
    for index, row in enumerate(targets):
        ax.annotate(row["region"].split("/")[-1], row["pca"], xytext=offsets[index],
                    textcoords="offset points", fontsize=9,
                    bbox={"facecolor": "white", "alpha": 0.85, "edgecolor": "none", "pad": 1})
    ax.axhline(0, color="#d5dbe0", linewidth=0.8)
    ax.axvline(0, color="#d5dbe0", linewidth=0.8)
    ax.grid(alpha=0.16, zorder=0)
    ax.set_xlabel(f"주성분 1: {ratios[0]:.2%} 설명")
    ax.set_ylabel(f"주성분 2: {ratios[1]:.2%} 설명")
    ax.legend(frameon=False, loc="lower left")
    fig.suptitle("용인시 39개 읍면동의 인구 특성", fontsize=19, fontweight="bold", y=0.96)
    fig.text(0.1, 0.885, f"표준화한 세 변수의 PCA 투영 | 두 축의 누적 설명 분산 {sum(ratios[:2]):.2%}", fontsize=11)
    fig.text(0.1, 0.065, "자료: 용인시 2026.10.31. 내국인 인구 및 세대, 연령별 인구 현황\n"
             "변수: 아동 비율, 고령 인구 비율, 세대당 인구. 군집은 원래의 3차원 특성에서 학습.", fontsize=9, color="#4d5965")
    save(fig, "pca")

    stability = report["stability"]
    fig, (left, right) = plt.subplots(1, 2, figsize=(12, 6.8), gridspec_kw={"width_ratios": [1.05, 1]})
    fig.subplots_adjust(left=0.07, right=0.96, top=0.79, bottom=0.21, wspace=0.65)
    for records, title, color in ((stability["seed_runs"], "초기값 변경", "#0072B2"),
                                  (stability["bootstrap_runs"], "지역 재표집", "#D55E00")):
        values = sorted(row["ari"] for row in records)
        left.step([values[0]] + values, [0] + [(i + 1) / len(values) for i in range(len(values))],
                  where="post", linewidth=2, label=f"{title} ({len(values)}회)", color=color)
    left.set(xlim=(-0.02, 1.02), ylim=(0, 1.04), xlabel="기준 군집과의 일치도 (ARI)", ylabel="누적 실험 비율")
    left.grid(alpha=0.18)
    left.legend(frameon=False, loc="upper left", fontsize=9)
    least_stable = sorted(rows, key=lambda row: (row["bootstrap_agreement"], row["region"]))[:10]
    positions = list(range(len(least_stable)))
    right.barh(positions, [row["bootstrap_agreement"] for row in least_stable],
               color=[colors[row["cluster"]] for row in least_stable], height=0.65)
    right.set_yticks(positions, [row["region"].split("/")[-1] for row in least_stable], fontsize=9)
    right.invert_yaxis()
    right.set(xlim=(0, 1.13), xlabel="기준 군집 유지 비율 (번호 대응 후)", title="배정이 가장 자주 달라진 10개 지역")
    for i, row in enumerate(least_stable):
        right.text(row["bootstrap_agreement"] + 0.018, i, f"{row['bootstrap_agreement']:.0%}", va="center", fontsize=9)
    right.xaxis.set_major_formatter(matplotlib.ticker.PercentFormatter(1))
    right.grid(axis="x", alpha=0.15)
    right.set_axisbelow(True)
    fig.suptitle("같은 지역 구분은 얼마나 유지될까?", fontsize=19, fontweight="bold", y=0.97)
    fig.text(0.07, 0.875, f"평균 ARI: 초기값 변경 {stability['seed_ari']['mean']:.3f} / 지역 재표집 {stability['bootstrap_ari']['mean']:.3f}", fontsize=12)
    fig.text(0.07, 0.07, f"지역 39개를 복원추출하고 매회 표준화와 군집을 다시 학습. 기존 39개 전체에 배정해 비교.\n"
             f"2개 지역인 군집 0이 학습 표본에서 모두 빠진 경우: {stability['bootstrap_omission_counts']['0']} / {stability['bootstrap_requested']}회. "
             "이 지표는 서비스 예측 정확도가 아님.", fontsize=9, color="#4d5965")
    save(fig, "stability")
    print(f"Rendered PCA and stability PNG/SVG figures using matplotlib {matplotlib.__version__}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--font-file", type=Path)
    args = parser.parse_args()
    render(args.report, args.output, args.font_file)
