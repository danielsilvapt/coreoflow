/* Treinador de Dança IA — avatar 3D (robô) que demonstra o passo.
 * Three.js + modelo RobotExpressive (via CDN). O robô dança em loop, com
 * controlo de velocidade e câmara livre; o treinador (GROQ) narra o passo e,
 * se o navegador suportar, o texto é lido em voz alta (pt-PT). */
(function () {
  if (window.cfAvatar) return;

  var THREE_URL = 'https://cdn.jsdelivr.net/npm/three@0.161.0/+esm';
  var GLTF_URL = 'https://cdn.jsdelivr.net/npm/three@0.161.0/examples/jsm/loaders/GLTFLoader.js/+esm';
  var ORBIT_URL = 'https://cdn.jsdelivr.net/npm/three@0.161.0/examples/jsm/controls/OrbitControls.js/+esm';
  var MODELS = [
    'https://cdn.jsdelivr.net/gh/mrdoob/three.js@r161/examples/models/gltf/RobotExpressive/RobotExpressive.glb',
    'https://threejs.org/examples/models/gltf/RobotExpressive/RobotExpressive.glb'
  ];

  function mkBtn(t) {
    var b = document.createElement('button');
    b.textContent = t;
    b.style.cssText = 'padding:7px 12px;border-radius:8px;border:1px solid #d1d5db;background:#fff;cursor:pointer;font:13px system-ui';
    return b;
  }

  window.cfAvatar = {
    _s: null,

    async iniciar(containerId, opts) {
      this.parar();
      var box = document.getElementById(containerId);
      if (!box) return;
      box.innerHTML = '';
      opts = opts || {};

      var status = document.createElement('div');
      status.style.cssText = 'font:13px system-ui;color:#555;margin-bottom:6px';
      status.textContent = 'A carregar o robô…';
      box.appendChild(status);

      var stage = document.createElement('div');
      stage.style.cssText = 'width:100%;height:340px;border-radius:12px;overflow:hidden;background:linear-gradient(160deg,#eef2ff,#e0e7ff)';
      box.appendChild(stage);

      var row = document.createElement('div');
      row.style.cssText = 'display:flex;gap:10px;align-items:center;margin-top:10px;flex-wrap:wrap';
      box.appendChild(row);
      var playBtn = mkBtn('⏸ Pausa');
      var velL = document.createElement('label');
      velL.style.cssText = 'font:13px system-ui;color:#374151';
      velL.textContent = 'Velocidade ';
      var vel = document.createElement('input');
      vel.type = 'range'; vel.min = '0.25'; vel.max = '1'; vel.step = '0.05'; vel.value = '0.65';
      vel.style.width = '120px';
      velL.appendChild(vel);
      var speakBtn = mkBtn('🔊 Ouvir explicação');
      row.append(playBtn, velL, speakBtn);

      var narr = document.createElement('div');
      narr.id = containerId + '-narr';
      narr.style.cssText = 'margin-top:10px;font:13px/1.55 system-ui;white-space:pre-wrap;background:#f5f3ff;border:1px solid #ddd6fe;border-radius:10px;padding:10px;min-height:40px';
      narr.textContent = 'O treinador está a preparar a explicação…';
      box.appendChild(narr);

      var hint = document.createElement('div');
      hint.style.cssText = 'font:11px system-ui;color:#9ca3af;margin-top:4px';
      hint.textContent = 'Arrasta para rodar a câmara · roda do rato para aproximar. O robô mostra a energia e o tempo do estilo; a técnica exata vem da explicação e dos vídeos.';
      box.appendChild(hint);

      var THREE, GLTFLoader, OrbitControls;
      try {
        THREE = await import(THREE_URL);
        GLTFLoader = (await import(GLTF_URL)).GLTFLoader;
        OrbitControls = (await import(ORBIT_URL)).OrbitControls;
      } catch (e) {
        status.textContent = 'Não foi possível carregar o 3D: ' + (e && e.message ? e.message : e);
        return;
      }

      var w = stage.clientWidth || 480;
      var h = stage.clientHeight || 340;
      var scene = new THREE.Scene();
      var camera = new THREE.PerspectiveCamera(45, w / h, 0.1, 100);
      camera.position.set(2.2, 2.4, 5.5);
      var renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
      renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
      renderer.setSize(w, h);
      stage.appendChild(renderer.domElement);

      scene.add(new THREE.HemisphereLight(0xffffff, 0x8d99b6, 2.4));
      var dir = new THREE.DirectionalLight(0xffffff, 2.2);
      dir.position.set(4, 10, 6);
      scene.add(dir);

      var controls = new OrbitControls(camera, renderer.domElement);
      controls.target.set(0, 1, 0);
      controls.enableDamping = true;
      controls.minDistance = 2.5;
      controls.maxDistance = 12;

      var s = {
        renderer: renderer, running: true, raf: 0, mixer: null,
        clock: new THREE.Clock(), action: null, resize: null, THREE: THREE
      };
      window.cfAvatar._s = s;

      var loader = new GLTFLoader();
      var onLoad = function (gltf) {
        if (status.parentNode) status.remove();
        var model = gltf.scene;
        model.position.y = 0;
        scene.add(model);
        s.mixer = new THREE.AnimationMixer(model);
        var clips = gltf.animations || [];
        var dance = THREE.AnimationClip.findByName(clips, 'Dance')
          || clips.find(function (c) { return /dance/i.test(c.name); })
          || clips[0];
        if (dance) {
          s.action = s.mixer.clipAction(dance);
          s.action.play();
          s.action.timeScale = parseFloat(vel.value);
        }
      };
      var tentar = function (i) {
        if (i >= MODELS.length) { status.textContent = 'Não foi possível carregar o modelo do robô.'; return; }
        loader.load(MODELS[i], onLoad, undefined, function () { tentar(i + 1); });
      };
      tentar(0);

      vel.oninput = function () { if (s.action) s.action.timeScale = parseFloat(vel.value); };
      playBtn.onclick = function () {
        s.running = !s.running;
        if (s.action) s.action.paused = !s.running;
        playBtn.textContent = s.running ? '⏸ Pausa' : '▶ Retomar';
      };
      speakBtn.onclick = function () { window.cfAvatar.falar(narr.textContent); };

      function animate() {
        s.raf = requestAnimationFrame(animate);
        var dt = s.clock.getDelta();
        if (s.mixer && s.running) s.mixer.update(dt);
        controls.update();
        renderer.render(scene, camera);
      }
      animate();

      s.resize = function () {
        var nw = stage.clientWidth || w, nh = stage.clientHeight || h;
        camera.aspect = nw / nh;
        camera.updateProjectionMatrix();
        renderer.setSize(nw, nh);
      };
      window.addEventListener('resize', s.resize);
    },

    narrar(containerId, texto) {
      var el = document.getElementById(containerId + '-narr');
      if (el) el.textContent = texto;
      this.falar(texto);
    },

    falar(texto) {
      try {
        if (!('speechSynthesis' in window) || !texto) return;
        window.speechSynthesis.cancel();
        var u = new SpeechSynthesisUtterance(texto);
        u.lang = 'pt-PT';
        u.rate = 0.98;
        var pick = function () {
          var vs = window.speechSynthesis.getVoices() || [];
          var pt = vs.find(function (v) { return /pt[-_]?pt/i.test(v.lang); })
            || vs.find(function (v) { return /^pt/i.test(v.lang); });
          if (pt) u.voice = pt;
          window.speechSynthesis.speak(u);
        };
        if ((window.speechSynthesis.getVoices() || []).length) pick();
        else window.speechSynthesis.onvoiceschanged = pick;
      } catch (e) { /* TTS indisponível — o texto fica na mesma no ecrã */ }
    },

    parar() {
      var s = window.cfAvatar._s;
      if (!s) return;
      try { cancelAnimationFrame(s.raf); } catch (e) {}
      try { window.removeEventListener('resize', s.resize); } catch (e) {}
      try { s.renderer.dispose(); } catch (e) {}
      try { s.renderer.domElement.remove(); } catch (e) {}
      try { window.speechSynthesis.cancel(); } catch (e) {}
      window.cfAvatar._s = null;
    }
  };
})();
