/* Treinador de Dança IA — avatar 3D que demonstra o passo.
 * Three.js (via jsdelivr +esm). Carrega um boneco humanoide e uma animação de
 * dança real; se falhar, recorre ao robô RobotExpressive. Câmara enquadra o
 * avatar por completo. O treinador (GROQ) narra o passo, lido em voz alta. */
(function () {
  if (window.cfAvatar) return;

  var THREE_URL = 'https://cdn.jsdelivr.net/npm/three@0.161.0/+esm';
  var GLTF_URL = 'https://cdn.jsdelivr.net/npm/three@0.161.0/examples/jsm/loaders/GLTFLoader.js/+esm';
  var ORBIT_URL = 'https://cdn.jsdelivr.net/npm/three@0.161.0/examples/jsm/controls/OrbitControls.js/+esm';

  // Boneco humanoide (rig Mixamo)
  var CHARS = [
    'https://cdn.jsdelivr.net/gh/mrdoob/three.js@r161/examples/models/gltf/Xbot.glb',
    'https://threejs.org/examples/models/gltf/Xbot.glb'
  ];
  // Animações de dança reais (Ready Player Me animation library — livre)
  var DANCES = [
    'https://cdn.jsdelivr.net/gh/readyplayerme/animation-library@master/masculine/glb/dance/M_Dances_001.glb',
    'https://cdn.jsdelivr.net/gh/readyplayerme/animation-library@master/masculine/glb/dance/M_Dances_002.glb',
    'https://cdn.jsdelivr.net/gh/readyplayerme/animation-library@master/masculine/glb/dance/M_Dances_003.glb',
    'https://cdn.jsdelivr.net/gh/readyplayerme/animation-library@master/masculine/glb/dance/M_Dances_004.glb',
    'https://cdn.jsdelivr.net/gh/readyplayerme/animation-library@master/masculine/glb/dance/M_Dances_005.glb',
    'https://cdn.jsdelivr.net/gh/readyplayerme/animation-library@master/masculine/glb/dance/M_Dances_006.glb'
  ];
  // Recurso: robô com uma animação de dança própria
  var ROBOTS = [
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
      var nextBtn = mkBtn('🔀 Outra dança');
      var velL = document.createElement('label');
      velL.style.cssText = 'font:13px system-ui;color:#374151';
      velL.textContent = 'Velocidade ';
      var vel = document.createElement('input');
      vel.type = 'range'; vel.min = '0.25'; vel.max = '1'; vel.step = '0.05'; vel.value = '0.7';
      vel.style.width = '110px';
      velL.appendChild(vel);
      var speakBtn = mkBtn('🔊 Ouvir explicação');
      row.append(playBtn, nextBtn, velL, speakBtn);

      var narr = document.createElement('div');
      narr.id = containerId + '-narr';
      narr.style.cssText = 'margin-top:10px;font:13px/1.55 system-ui;white-space:pre-wrap;background:#f5f3ff;border:1px solid #ddd6fe;border-radius:10px;padding:10px;min-height:40px';
      narr.textContent = 'O treinador está a preparar a explicação…';
      box.appendChild(narr);

      var hint = document.createElement('div');
      hint.style.cssText = 'font:11px system-ui;color:#9ca3af;margin-top:4px';
      hint.textContent = 'Arrasta para rodar · roda do rato para aproximar. As danças são genéricas — mostram a energia e o tempo; a técnica do passo vem da explicação e dos vídeos.';
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
      var camera = new THREE.PerspectiveCamera(42, w / h, 0.1, 200);
      var renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
      renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
      renderer.setSize(w, h);
      stage.appendChild(renderer.domElement);

      scene.add(new THREE.HemisphereLight(0xffffff, 0x8d99b6, 2.6));
      var dl = new THREE.DirectionalLight(0xffffff, 2.2);
      dl.position.set(4, 10, 6);
      scene.add(dl);
      var grid = new THREE.GridHelper(10, 20, 0xc7d2fe, 0xe0e7ff);
      grid.position.y = 0;
      scene.add(grid);

      var controls = new OrbitControls(camera, renderer.domElement);
      controls.enableDamping = true;
      controls.minDistance = 1.5;
      controls.maxDistance = 30;

      var loader = new GLTFLoader();

      var s = {
        renderer: renderer, running: true, raf: 0, mixer: null,
        clock: new THREE.Clock(), action: null, model: null, danceClips: [],
        danceIdx: 0, resize: null, THREE: THREE, isRobot: false
      };
      window.cfAvatar._s = s;

      function loadFirst(urls, ok, fail) {
        var i = 0;
        var go = function () {
          if (i >= urls.length) { fail && fail(); return; }
          loader.load(urls[i], ok, undefined, function () { i++; go(); });
        };
        go();
      }

      function enquadrar(model) {
        var b = new THREE.Box3().setFromObject(model);
        var size = b.getSize(new THREE.Vector3());
        var center = b.getCenter(new THREE.Vector3());
        var maxDim = Math.max(size.x, size.y, size.z);
        var dist = (maxDim / 2) / Math.tan(THREE.MathUtils.degToRad(camera.fov) / 2);
        dist *= 1.6;
        camera.position.set(center.x + dist * 0.35, center.y + size.y * 0.15, center.z + dist);
        camera.near = dist / 100;
        camera.far = dist * 100;
        camera.updateProjectionMatrix();
        controls.target.copy(center);
        controls.update();
        grid.position.y = b.min.y;
      }

      function boneNames(model) {
        var set = new Set();
        model.traverse(function (o) { if (o.isBone) set.add(o.name); });
        return set;
      }

      // Adapta os nomes das tracks da animação ao esqueleto do modelo
      function retarget(clip, bones) {
        var usaMixamo = false;
        bones.forEach(function (n) { if (n.indexOf('mixamorig') === 0) usaMixamo = true; });
        var tracks = [];
        clip.tracks.forEach(function (tr) {
          var dot = tr.name.indexOf('.');
          if (dot < 0) return;
          var node = tr.name.slice(0, dot);
          var prop = tr.name.slice(dot);
          if (usaMixamo && node.indexOf('mixamorig') !== 0) node = 'mixamorig' + node;
          if (!usaMixamo && node.indexOf('mixamorig') === 0) node = node.slice(9);
          if (bones.has(node)) {
            var t2 = tr.clone();
            t2.name = node + prop;
            tracks.push(t2);
          }
        });
        return tracks.length ? new THREE.AnimationClip(clip.name || 'dance', clip.duration, tracks) : null;
      }

      function tocar(idx) {
        if (!s.mixer || !s.danceClips.length) return;
        s.danceIdx = ((idx % s.danceClips.length) + s.danceClips.length) % s.danceClips.length;
        if (s.action) s.action.fadeOut(0.2);
        s.action = s.mixer.clipAction(s.danceClips[s.danceIdx]);
        s.action.reset();
        s.action.timeScale = parseFloat(vel.value);
        s.action.fadeIn(0.2);
        s.action.play();
      }

      function usarRobot() {
        s.isRobot = true;
        nextBtn.style.display = 'none';
        loadFirst(ROBOTS, function (gltf) {
          if (status.parentNode) status.remove();
          s.model = gltf.scene;
          scene.add(s.model);
          enquadrar(s.model);
          s.mixer = new THREE.AnimationMixer(s.model);
          var clips = gltf.animations || [];
          var dance = THREE.AnimationClip.findByName(clips, 'Dance')
            || clips.find(function (c) { return /dance/i.test(c.name); }) || clips[0];
          if (dance) { s.danceClips = [dance]; tocar(0); }
        }, function () { status.textContent = 'Não foi possível carregar o avatar.'; });
      }

      // 1) boneco humanoide
      loadFirst(CHARS, function (gltf) {
        s.model = gltf.scene;
        s.model.traverse(function (o) { if (o.isMesh) { o.castShadow = true; o.frustumCulled = false; } });
        scene.add(s.model);
        enquadrar(s.model);
        s.mixer = new THREE.AnimationMixer(s.model);
        var bones = boneNames(s.model);

        // 2) animações de dança
        var carregadas = 0, tentativas = 0;
        DANCES.forEach(function (url) {
          loader.load(url, function (g) {
            tentativas++;
            (g.animations || []).forEach(function (cl) {
              var r = retarget(cl, bones);
              if (r) s.danceClips.push(r);
            });
            carregadas++;
            if (s.danceClips.length && !s.action) { if (status.parentNode) status.remove(); tocar(0); }
            if (tentativas === DANCES.length && !s.danceClips.length) usarRobot();
          }, undefined, function () {
            tentativas++;
            if (tentativas === DANCES.length && !s.danceClips.length) usarRobot();
          });
        });
      }, function () { usarRobot(); });

      vel.oninput = function () { if (s.action) s.action.timeScale = parseFloat(vel.value); };
      playBtn.onclick = function () {
        s.running = !s.running;
        if (s.action) s.action.paused = !s.running;
        playBtn.textContent = s.running ? '⏸ Pausa' : '▶ Retomar';
      };
      nextBtn.onclick = function () { tocar(s.danceIdx + 1); };
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
