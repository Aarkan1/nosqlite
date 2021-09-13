let ws;

function connect() {
  ws = new WebSocket('ws://localhost:9595/events/users')
  console.log('[WS] connecting');
  
  ws.onmessage = e => {
    console.log(JSON.parse(e.data));
  }

  ws.onopen = () => {
    console.log('[WS] connected');

    setTimeout(() => {
      send('Yay connection complete!')
    }, 500);
  }
  
  ws.onclose = () => {
    console.log('[WS] closed');
    setTimeout(() => {
      connect()
    }, 1000);
  }
}

function send(data) {
  if(ws) {
    ws.send(JSON.stringify(data))
  }
}

connect()