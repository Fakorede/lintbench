import runpy, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
runpy.run_module("curate", run_name="__main__", alter_sys=True)
