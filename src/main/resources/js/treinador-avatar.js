/* Treinador de Dança IA — avatar 3D (robô) que demonstra o passo.
 * Three.js (via jsdelivr +esm) + modelo RobotExpressive, que traz uma animação
 * de dança limpa e já pronta (sem retargeting). Câmara enquadra o avatar por
 * completo. O treinador (GROQ) narra o passo, lido em voz alta (pt-PT). */
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
      status.textContent = 'A carregar o avatar…';
      box.appendChild(status);

      var stage = document.createElement('div');
      stage.style.cssText = 'width:100%;height:380px;border-radius:12px;overflow:hidden;background:linear-gradient(160deg,#eef2ff,#e0e7ff)';
      box.appendChild(stage);

      var row = document.createElement('div');
      row.style.cssText = 'display:flex;gap:10px;align-items:center;margin-top:10px;flex-wrap:wrap';
      box.appendChild(row);
      var playBtn = mkBtn('⏸ Pausa');
      var moveSel = document.createElement('select');
      moveSel.style.cssText = 'padding:6px 8px;border-radius:8px;border:1px solid #d1d5db;font:13px system-ui';
      var velL = document.createElement('label');
      velL.style.cssText = 'font:13px system-ui;color:#374151';
      velL.textContent = 'Velocidade ';
      var vel = document.createElement('input');
      vel.type = 'range'; vel.min = '0.25'; vel.max = '1.25'; vel.step = '0.05'; vel.value = '0.75';
      vel.style.width = '110px';
      velL.appendChild(vel);
      var speakBtn = mkBtn('🔊 Ouvir explicação');
      row.append(playBtn, moveSel, velL, speakBtn);

      var narr = document.createElement('div');
      narr.id = containerId + '-narr';
      narr.style.cssText = 'margin-top:10px;font:13px/1.55 system-ui;white-space:pre-wrap;background:#f5f3ff;border:1px solid #ddd6fe;border-radius:10px;padding:10px;min-height:40px';
      narr.textContent = 'O treinador está a preparar a explicação…';
      box.appendChild(narr);

      var hint = document.createElement('div');
      hint.style.cssText = 'font:11px system-ui;color:#9ca3af;margin-top:4px';
      hint.textContent = 'Arrasta para rodar · roda do rato para aproximar. O robô demonstra o tempo e a energia; a técnica do passo vem da explicação e dos vídeos.';
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

      var w = stage.clientWidth || 520;
      var h = stage.clientHeight || 380;
      var scene = new THREE.Scene();
      var camera = new THREE.PerspectiveCamera(42, w / h, 0.1, 500);
      var renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
      renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
      renderer.setSize(w, h);
      stage.appendChild(renderer.domElement);

      scene.add(new THREE.HemisphereLight(0xffffff, 0x8d99b6, 2.6));
      var dl = new THREE.DirectionalLight(0xffffff, 2.2);
      dl.position.set(4, 10, 6);
      scene.add(dl);

      var controls = new OrbitControls(camera, renderer.domElement);
      controls.enableDamping = true;

      var s = {
        renderer: renderer, running: true, raf: 0, mixer: null,
        clock: new THREE.Clock(), action: null, actions: {}, resize: null, THREE: THREE
      };
      window.cfAvatar._s = s;

      function enquadrar(model) {
        var b = new THREE.Box3().setFromObject(model);
        var size = b.getSize(new THREE.Vector3());
        var center = b.getCenter(new THREE.Vector3());
        var maxDim = Math.max(size.x, size.y, size.z);
        var dist = (maxDim / 2) / Math.tan(THREE.MathUtils.degToRad(camera.fov) / 2) * 1.7;
        camera.position.set(center.x + dist * 0.25, center.y + size.y * 0.05, center.z + dist);
        camera.near = Math.max(0.01, dist / 200);
        camera.far = dist * 200;
        camera.updateProjectionMatrix();
        controls.target.copy(center);
        controls.minDistance = maxDim * 0.6;
        controls.maxDistance = dist * 4;
        controls.update();

        var grid = new THREE.GridHelper(maxDim * 6, 24, 0xc7d2fe, 0xe4e7ff);
        grid.position.y = b.min.y;
        scene.add(grid);
      }

      function trocar(nome) {
        var next = s.actions[nome];
        if (!next || next === s.action) return;
        if (s.action) s.action.fadeOut(0.25);
        next.reset();
        next.timeScale = parseFloat(vel.value);
        next.fadeIn(0.25);
        next.play();
        s.action = next;
      }

      function carregar(i) {
        if (i >= MODELS.length) { status.textContent = 'Não foi possível carregar o avatar.'; return; }
        new GLTFLoader().load(MODELS[i], function (gltf) {
          if (status.parentNode) status.remove();
          var model = gltf.scene;
          scene.add(model);
          enquadrar(model);

          s.mixer = new THREE.AnimationMixer(model);
          var preferidas = ['Dance', 'Wave', 'Jump', 'ThumbsUp', 'Idle', 'Walking', 'Running'];
          (gltf.animations || []).forEach(function (cl) {
            s.actions[cl.name] = s.mixer.clipAction(cl);
          });
          // dropdown de movimentos disponíveis (Dança primeiro)
          var nomes = Object.keys(s.actions).sort(function (a, b) {
            var ia = preferidas.indexOf(a); var ib = preferidas.indexOf(b);
            return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib);
          });
          nomes.forEach(function (n) {
            var o = document.createElement('option');
            o.value = n;
            o.textContent = (n === 'Dance' ? 'Dança' : n);
            moveSel.appendChild(o);
          });
          var inicial = s.actions['Dance'] ? 'Dance' : nomes[0];
          moveSel.value = inicial;
          trocar(inicial);
        }, undefined, function () { carregar(i + 1); });
      }
      carregar(0);

      vel.oninput = function () { if (s.action) s.action.timeScale = parseFloat(vel.value); };
      moveSel.onchange = function () { trocar(moveSel.value); };
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
      } catch (e) { /* TTS indisponível */ }
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
