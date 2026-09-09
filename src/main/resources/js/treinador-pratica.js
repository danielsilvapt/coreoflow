/* Treinador de Dança IA — modo prática (fase 2).
 * Webcam + MediaPipe PoseLandmarker no browser (nada sai do dispositivo).
 * Mostra o esqueleto, um metrónomo e métricas aproximadas de movimento;
 * a gravação de 15 s é resumida em estatísticas e enviada ao servidor para
 * o treinador (GROQ) dar feedback em texto. */
(function () {
  if (window.cfPratica) return;

  var PKG = 'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14';
  var MODEL = 'https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task';
  var CONN = [[11,12],[11,13],[13,15],[12,14],[14,16],[11,23],[12,24],[23,24],
              [23,25],[25,27],[24,26],[26,28],[27,31],[28,32],[0,11],[0,12]];

  function btnCss(bg, fg) {
    return 'padding:7px 12px;border-radius:8px;border:1px solid #d1d5db;background:'
      + (bg || '#fff') + ';color:' + (fg || '#111') + ';cursor:pointer;font:13px system-ui';
  }
  // BPM típico por estilo (aproximado — o metrónomo marca o tempo de dança)
  var BPM_ESTILO = {
    'kizomba': 95, 'semba': 130, 'salsa': 180, 'bachata': 125, 'merengue': 140,
    'tango': 120, 'valsa': 170, 'foxtrote': 120, 'chachacha': 120, 'cha cha': 120,
    'rumba': 100, 'samba': 100, 'jive': 176, 'paso doble': 120,
    'hip hop': 95, 'hip-hop': 95, 'hiphop': 95, 'breakdance': 110, 'popping': 100,
    'house': 125, 'dancehall': 100, 'afro': 108, 'afrobeat': 108,
    'contemporaneo': 100, 'moderno': 100, 'lirico': 90, 'jazz': 120,
    'ballet': 100, 'classico': 100, 'sapateado': 180, 'tap': 180,
    'danca do ventre': 100, 'flamenco': 120, 'zumba': 130, 'danca de salao': 120,
    'ballroom': 120, 'swing': 160, 'lindy hop': 165, 'rock and roll': 180
  };
  function norml(s) {
    var t = (s || '').toLowerCase().normalize('NFD');
    var out = '';
    for (var i = 0; i < t.length; i++) {
      var c = t.charCodeAt(i);
      if (c < 0x0300 || c > 0x036f) out += t[i];
    }
    return out.trim();
  }
  function bpmSugerido(estilo) {
    var k = norml(estilo);
    if (!k) return 100;
    var keys = Object.keys(BPM_ESTILO);
    for (var i = 0; i < keys.length; i++) {
      if (k.indexOf(norml(keys[i])) >= 0 || norml(keys[i]).indexOf(k) >= 0) return BPM_ESTILO[keys[i]];
    }
    return 100;
  }

  function clamp(v) { return Math.max(0, Math.min(1, v)); }
  function pct(v) { return Math.round(v * 100) + '%'; }
  function avg(a) { return a.length ? a.reduce(function (x, y) { return x + y; }, 0) / a.length : 0; }
  function stdev(a) { var m = avg(a); return Math.sqrt(avg(a.map(function (x) { return (x - m) * (x - m); }))); }

  window.cfPratica = {

    mostrar: function (containerId, txt) {
      var el = document.getElementById(containerId + '-fb');
      if (el) { el.style.display = 'block'; el.textContent = txt; }
    },

    parar: function () {
      var s = window.cfPratica._state;
      if (!s) return;
      try { cancelAnimationFrame(s.raf); } catch (e) {}
      try { clearInterval(s.metroTimer); } catch (e) {}
      try { (s.stream.getTracks() || []).forEach(function (t) { t.stop(); }); } catch (e) {}
      try { s.landmarker && s.landmarker.close && s.landmarker.close(); } catch (e) {}
      window.cfPratica._state = null;
    },

    iniciar: async function (containerId, serverEl, opts) {
      window.cfPratica.parar();
      var box = document.getElementById(containerId);
      if (!box) return;
      box.innerHTML = '';
      opts = opts || {};

      var status = document.createElement('div');
      status.style.cssText = 'font:13px system-ui;color:#555;margin-bottom:6px';
      status.textContent = 'A carregar o modelo de pose…';
      box.appendChild(status);

      var wrap = document.createElement('div');
      wrap.style.cssText = 'position:relative;width:100%;max-width:520px;background:#000;border-radius:12px;overflow:hidden';
      var video = document.createElement('video');
      video.autoplay = true; video.playsInline = true; video.muted = true;
      video.style.cssText = 'width:100%;display:block;transform:scaleX(-1)';
      var canvas = document.createElement('canvas');
      canvas.style.cssText = 'position:absolute;left:0;top:0;width:100%;height:100%;transform:scaleX(-1)';
      wrap.appendChild(video); wrap.appendChild(canvas);
      box.appendChild(wrap);

      var meters = document.createElement('div');
      meters.style.cssText = 'display:flex;flex-wrap:wrap;gap:10px;margin-top:10px';
      box.appendChild(meters);
      function mk(label) {
        var d = document.createElement('div');
        d.style.cssText = 'flex:1;min-width:110px;background:#fff;border-radius:10px;padding:8px 10px;box-shadow:0 1px 4px rgba(0,0,0,.08)';
        var v = document.createElement('div');
        v.style.cssText = 'font-size:20px;font-weight:800;color:#8B5CF6'; v.textContent = '–';
        var l = document.createElement('div');
        l.style.cssText = 'font-size:11px;color:#6b7280;text-transform:uppercase'; l.textContent = label;
        d.appendChild(v); d.appendChild(l); meters.appendChild(d); return v;
      }
      var mMov = mk('Movimento'), mAmp = mk('Amplitude'), mSim = mk('Simetria'),
          mPost = mk('Postura'), mRit = mk('No ritmo');

      var bpmSug = bpmSugerido(opts.estilo);
      var controls = document.createElement('div');
      controls.style.cssText = 'display:flex;gap:10px;align-items:center;margin-top:10px;flex-wrap:wrap';
      box.appendChild(controls);
      var bpmL = document.createElement('label');
      bpmL.style.cssText = 'font:13px system-ui;color:#374151';
      bpmL.textContent = 'BPM: ';
      var bpm = document.createElement('input');
      bpm.type = 'number'; bpm.value = opts.bpm || bpmSug; bpm.min = 40; bpm.max = 240;
      bpm.style.cssText = 'width:64px';
      bpmL.appendChild(bpm);
      var bpmHint = document.createElement('span');
      bpmHint.style.cssText = 'font:12px system-ui;color:#8B5CF6';
      bpmHint.textContent = opts.estilo ? ('sugerido p/ ' + opts.estilo + ': ' + bpmSug) : '';
      var metroBtn = document.createElement('button');
      metroBtn.textContent = '▶ Metrónomo'; metroBtn.style.cssText = btnCss();
      var recBtn = document.createElement('button');
      recBtn.textContent = '● Gravar 15s e analisar'; recBtn.style.cssText = btnCss('#8B5CF6', '#fff');
      controls.appendChild(bpmL); controls.appendChild(bpmHint);
      controls.appendChild(metroBtn); controls.appendChild(recBtn);

      var fb = document.createElement('div');
      fb.id = containerId + '-fb';
      fb.style.cssText = 'margin-top:10px;font:13px/1.5 system-ui;color:#111;white-space:pre-wrap;background:#f5f3ff;border:1px solid #ddd6fe;border-radius:10px;padding:10px;display:none';
      box.appendChild(fb);

      var s = { raf: 0, metroTimer: 0, stream: null, landmarker: null };
      window.cfPratica._state = s;

      try {
        s.stream = await navigator.mediaDevices.getUserMedia({ video: { width: 640, height: 480 }, audio: false });
        video.srcObject = s.stream;
        await video.play();
      } catch (e) {
        status.textContent = 'Não foi possível aceder à câmara: ' + e.message;
        return;
      }

      var vision;
      try {
        try {
          vision = await import(PKG + '/vision_bundle.mjs');
        } catch (e1) {
          vision = await import(PKG + '/+esm');
        }
        var resolver = await vision.FilesetResolver.forVisionTasks(PKG + '/wasm');
        s.landmarker = await vision.PoseLandmarker.createFromOptions(resolver, {
          baseOptions: { modelAssetPath: MODEL, delegate: 'GPU' },
          runningMode: 'VIDEO', numPoses: 1
        });
      } catch (e) {
        status.textContent = 'Falha a carregar o modelo de pose: ' + (e && e.message ? e.message : e);
        return;
      }
      status.textContent = 'Faz o passo em frente à câmara. As métricas são só um guia — o feedback vem do treinador.';

      var ctx = canvas.getContext('2d');
      var prev = null, lastT = performance.now();
      var histMov = [], ampBuf = {}, motionPeaks = [];
      var recording = false, recFrames = [];
      var metroOn = false, actx = null, beatTimes = [], beatCount = 0;

      function curBpm() { return Math.max(40, Math.min(240, +bpm.value || bpmSug || 100)); }

      function tick() {
        try {
          var now = actx.currentTime;
          var o = actx.createOscillator(), g = actx.createGain();
          // acento no 1º tempo de cada 4
          var acento = (beatCount % 4) === 0;
          o.frequency.value = acento ? 1400 : 900;
          o.type = 'square';
          g.gain.setValueAtTime(acento ? 0.22 : 0.13, now);
          g.gain.exponentialRampToValueAtTime(0.0008, now + 0.06);
          o.connect(g); g.connect(actx.destination);
          o.start(now); o.stop(now + 0.07);
        } catch (e) {}
        beatCount++;
        beatTimes.push(performance.now());
        if (beatTimes.length > 16) beatTimes.shift();
        metroBtn.style.background = '#8B5CF6'; metroBtn.style.color = '#fff';
        setTimeout(function () { metroBtn.style.background = '#fff'; metroBtn.style.color = '#111'; }, 90);
      }

      function pararMetro() {
        metroOn = false;
        clearInterval(s.metroTimer);
        metroBtn.textContent = '▶ Metrónomo';
        metroBtn.style.background = '#fff'; metroBtn.style.color = '#111';
      }

      metroBtn.onclick = function () {
        if (metroOn) { pararMetro(); return; }
        try {
          actx = actx || new (window.AudioContext || window.webkitAudioContext)();
          if (actx.state === 'suspended' && actx.resume) { actx.resume(); }
        } catch (e) {
          status.textContent = 'Sem áudio neste navegador: ' + (e && e.message ? e.message : e);
          return;
        }
        metroOn = true; beatCount = 0;
        tick();
        s.metroTimer = setInterval(tick, 60000 / curBpm());
        metroBtn.textContent = '■ Parar metrónomo';
      };

      bpm.onchange = function () {
        if (metroOn) {
          clearInterval(s.metroTimer);
          s.metroTimer = setInterval(tick, 60000 / curBpm());
        }
      };

      function seg(lm, i, pv, dt) {
        if (!pv || !lm[i] || !pv[i]) return 0;
        return Math.hypot(lm[i].x - pv[i].x, lm[i].y - pv[i].y) / dt * 1000;
      }
      function speed(lm, dt) {
        if (!prev) return 0;
        var t = 0, n = 0;
        [15, 16, 27, 28, 11, 12, 23, 24].forEach(function (i) {
          if (lm[i] && prev[i]) { t += Math.hypot(lm[i].x - prev[i].x, lm[i].y - prev[i].y); n++; }
        });
        return n ? (t / n) / dt * 1000 : 0;
      }
      function ritmoScore() {
        if (beatTimes.length < 3 || motionPeaks.length < 10) return 0;
        var maxima = [];
        for (var i = 2; i < motionPeaks.length - 2; i++) {
          var p = motionPeaks[i];
          if (p.v > motionPeaks[i - 1].v && p.v > motionPeaks[i + 1].v && p.v > 0.06) maxima.push(p.t);
        }
        if (!maxima.length) return 0;
        var iv = (beatTimes[beatTimes.length - 1] - beatTimes[0]) / (beatTimes.length - 1);
        var hit = 0;
        maxima.forEach(function (mt) {
          var phase = ((mt - beatTimes[0]) % iv + iv) % iv;
          var off = Math.min(phase, iv - phase) / iv;
          if (off < 0.18) hit++;
        });
        return hit / maxima.length;
      }

      function loop() {
        if (!s.landmarker) return;
        var now = performance.now();
        var dt = Math.max(1, now - lastT); lastT = now;
        var res;
        try { res = s.landmarker.detectForVideo(video, now); } catch (e) { res = null; }

        canvas.width = wrap.clientWidth; canvas.height = wrap.clientHeight;
        ctx.clearRect(0, 0, canvas.width, canvas.height);

        if (res && res.landmarks && res.landmarks[0]) {
          var lm = res.landmarks[0];
          ctx.strokeStyle = '#8B5CF6'; ctx.lineWidth = 3;
          CONN.forEach(function (c) {
            var a = lm[c[0]], b = lm[c[1]];
            if (a && b) {
              ctx.beginPath();
              ctx.moveTo(a.x * canvas.width, a.y * canvas.height);
              ctx.lineTo(b.x * canvas.width, b.y * canvas.height);
              ctx.stroke();
            }
          });
          ctx.fillStyle = '#22d3ee';
          lm.forEach(function (p) {
            ctx.beginPath(); ctx.arc(p.x * canvas.width, p.y * canvas.height, 4, 0, 7); ctx.fill();
          });

          var sp = speed(lm, dt);
          histMov.push(sp); if (histMov.length > 60) histMov.shift();
          var mov = avg(histMov);

          [15, 16, 27, 28].forEach(function (i) {
            ampBuf[i] = ampBuf[i] || [];
            if (lm[i]) { ampBuf[i].push({ x: lm[i].x, y: lm[i].y }); if (ampBuf[i].length > 50) ampBuf[i].shift(); }
          });
          var amp = 0, na = 0;
          Object.keys(ampBuf).forEach(function (k) {
            var arr = ampBuf[k];
            if (arr.length > 5) {
              var xs = arr.map(function (p) { return p.x; }), ys = arr.map(function (p) { return p.y; });
              amp += Math.hypot(Math.max.apply(null, xs) - Math.min.apply(null, xs),
                                Math.max.apply(null, ys) - Math.min.apply(null, ys));
              na++;
            }
          });
          amp = na ? amp / na : 0;

          var lS = seg(lm, 15, prev, dt) + seg(lm, 27, prev, dt);
          var rS = seg(lm, 16, prev, dt) + seg(lm, 28, prev, dt);
          var sym = (lS + rS) > 1e-4 ? 1 - Math.abs(lS - rS) / (lS + rS) : 1;

          var post = 1;
          if (lm[11] && lm[12] && lm[23] && lm[24]) {
            var shTilt = Math.abs(lm[11].y - lm[12].y);
            var midShX = (lm[11].x + lm[12].x) / 2;
            var midHipX = (lm[23].x + lm[24].x) / 2;
            post = Math.max(0, 1 - shTilt * 4 - Math.abs(midShX - midHipX) * 3);
          }

          motionPeaks.push({ t: now, v: sp });
          if (motionPeaks.length > 90) motionPeaks.shift();
          var ritmo = ritmoScore();

          mMov.textContent = pct(clamp(mov / 0.35));
          mAmp.textContent = pct(clamp(amp / 0.9));
          mSim.textContent = pct(clamp(sym));
          mPost.textContent = pct(clamp(post));
          mRit.textContent = metroOn ? pct(clamp(ritmo)) : '–';

          if (recording) recFrames.push({ t: now, mov: mov, amp: amp, sym: sym, post: post, ritmo: ritmo, sp: sp });
          prev = lm.map(function (p) { return { x: p.x, y: p.y }; });
        }
        s.raf = requestAnimationFrame(loop);
      }

      recBtn.onclick = function () {
        if (recording) return;
        recording = true; recFrames = []; fb.style.display = 'none';
        recBtn.textContent = '● A gravar…'; recBtn.disabled = true;
        setTimeout(function () {
          recording = false; recBtn.textContent = '● Gravar 15s e analisar'; recBtn.disabled = false;
          if (recFrames.length < 10) {
            fb.style.display = 'block';
            fb.textContent = 'Não deu para captar movimento suficiente. Afasta-te para caber o corpo todo e tenta outra vez.';
            return;
          }
          var g = function (k) { return recFrames.map(function (f) { return f[k]; }); };
          var resumo = {
            segundos: Math.round((recFrames[recFrames.length - 1].t - recFrames[0].t) / 1000),
            movimento: +avg(g('mov')).toFixed(3),
            amplitude: +avg(g('amp')).toFixed(3),
            simetria: +avg(g('sym')).toFixed(2),
            postura: +avg(g('post')).toFixed(2),
            ritmo: metroOn ? +avg(g('ritmo')).toFixed(2) : null,
            bpm: metroOn ? (+bpm.value || null) : null,
            variabilidade: +stdev(g('sp')).toFixed(3)
          };
          var aviso = 'A analisar com o treinador…';
          fb.style.display = 'block';
          fb.textContent = aviso;
          try {
            serverEl.$server.analisarPratica(JSON.stringify(resumo));
          } catch (e) {
            fb.textContent = 'Erro a enviar para análise: ' + e;
            return;
          }
          setTimeout(function () {
            if (fb.textContent === aviso) {
              fb.textContent = 'A análise está a demorar mais do que o normal — verifica a chave GROQ em Configurações e tenta outra vez.';
            }
          }, 80000);
        }, 15000);
      };

      loop();
    }
  };
})();
