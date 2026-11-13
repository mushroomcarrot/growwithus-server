"""Explicit setup command. The inference server itself never downloads models."""
import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", choices=("base", "small"), default="base")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    from faster_whisper.utils import download_model
    download_model(args.model, output_dir=str(args.output))


if __name__ == "__main__":
    main()
