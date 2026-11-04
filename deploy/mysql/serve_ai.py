"""Local AI server wrapper with a filesystem shutdown signal, not a public shutdown API."""
from pathlib import Path
import argparse,threading,time
import uvicorn

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--port',type=int,required=True);parser.add_argument('--stop-file',required=True);args=parser.parse_args()
    server=uvicorn.Server(uvicorn.Config('app.main:app',host='127.0.0.1',port=args.port,access_log=False))
    def watch():
        while not Path(args.stop_file).exists():time.sleep(.3)
        server.should_exit=True
    threading.Thread(target=watch,daemon=True).start();server.run()
if __name__=='__main__':main()
