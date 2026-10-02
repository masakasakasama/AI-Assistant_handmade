import * as T from './node_modules/three/build/three.module.js';
const scene=new T.Scene();
const renderer=new T.WebGLRenderer({antialias:true,alpha:true,preserveDrawingBuffer:true});
renderer.setSize(innerWidth,innerHeight);renderer.setPixelRatio(1);renderer.outputColorSpace=T.SRGBColorSpace;renderer.toneMapping=T.ACESFilmicToneMapping;renderer.toneMappingExposure=1.08;renderer.shadowMap.enabled=true;renderer.shadowMap.type=T.PCFSoftShadowMap;document.body.appendChild(renderer.domElement);
const aspect=innerWidth/innerHeight;const v=4.35;const camera=new T.OrthographicCamera(-v*aspect/2,v*aspect/2,v/2,-v/2,.1,30);camera.position.set(0,2.25,7.6);camera.lookAt(0,1.42,0);
scene.add(new T.HemisphereLight(0xe6f5ff,0xd8c5bc,1.7));
const key=new T.DirectionalLight(0xfff5e8,2.8);key.position.set(-3.5,5.5,5);key.castShadow=true;key.shadow.mapSize.set(1024,1024);key.shadow.camera.left=-3;key.shadow.camera.right=3;key.shadow.camera.top=4;key.shadow.camera.bottom=-2;key.shadow.normalBias=.025;key.shadow.bias=-.00025;key.shadow.radius=3;scene.add(key);
const fill=new T.DirectionalLight(0xcce8ff,.9);fill.position.set(3,2.8,3);scene.add(fill);
const rim=new T.DirectionalLight(0xffd9e8,1.8);rim.position.set(2,4,-3);scene.add(rim);
const white=new T.MeshPhysicalMaterial({color:0xf9f5ef,roughness:.96,metalness:0,sheen:.65,sheenColor:0xffffff,sheenRoughness:1});
const cream=new T.MeshStandardMaterial({color:0xfff9f1,roughness:.92});
const pink=new T.MeshStandardMaterial({color:0xf2a5b9,roughness:.9});
const eyeMat=new T.MeshPhysicalMaterial({color:0x269cd2,roughness:.38,metalness:0,clearcoat:.22,clearcoatRoughness:.35});
const brown=new T.MeshStandardMaterial({color:0x785248,roughness:.78});
const highlight=new T.MeshBasicMaterial({color:0xffffff});
const sphere=new T.SphereGeometry(1,48,32);
function mesh(parent,mat,p,s,geom=sphere){const m=new T.Mesh(geom,mat);m.position.set(...p);m.scale.set(...s);m.castShadow=true;m.receiveShadow=true;parent.add(m);return m}
const root=new T.Group();scene.add(root);
const body=mesh(root,white,[0,.75,-.09],[.59,.65,.47]);
// Body stays a single soft shape: no hard belly plate.
const feet=[];for(const sign of[-1,1]){const f=mesh(root,white,[sign*.35,.19,.19],[.29,.19,.37]);f.rotation.y=sign*.15;feet.push(f);const paw=mesh(root,white,[sign*.185,.96,.41],[.19,.27,.20]);paw.rotation.z=sign*-.62;}
const head=new T.Group();head.position.set(0,1.86,.05);root.add(head);mesh(head,white,[0,0,0],[1.02,.84,.71]);
// Surface-following local mounts are essential: facial details must clear the curved head.
function surface(x,y,offset=0){const z=.71*Math.sqrt(Math.max(0,1-(x/1.02)**2-(y/.84)**2));const n=new T.Vector3(x/(1.02**2),y/(.84**2),z/(.71**2)).normalize();return{p:new T.Vector3(x,y,z).addScaledVector(n,offset),n}}
const eyes=[];for(const sign of[-1,1]){const mount=surface(sign*.36,.015,.004);const g=new T.Group();g.position.copy(mount.p);g.quaternion.setFromUnitVectors(new T.Vector3(0,0,1),mount.n);head.add(g);mesh(g,eyeMat,[0,0,0],[.13,.174,.030]);mesh(g,highlight,[-.035,.059,.027],[.030,.035,.004]);mesh(g,highlight,[.040,-.068,.024],[.010,.013,.003]);eyes.push(g);const ch=surface(sign*.61,-.255,.008);const c=mesh(head,pink,[ch.p.x,ch.p.y,ch.p.z],[.165,.091,.019]);c.quaternion.setFromUnitVectors(new T.Vector3(0,0,1),ch.n);}
function tube(parent,pts,r,mat,segments=48){const c=new T.CatmullRomCurve3(pts.map(v=>new T.Vector3(...v)));return mesh(parent,mat,[0,0,0],[1,1,1],new T.TubeGeometry(c,segments,r,8,false));}
const mouth2d=[[-.159,-.24],[-.137,-.28],[-.09,-.294],[-.045,-.282],[0,-.246],[.045,-.282],[.09,-.294],[.137,-.28],[.159,-.24]];
tube(head,mouth2d.map(([x,y])=>{const s=surface(x,y,.018);return[s.p.x,s.p.y,s.p.z]}),.018,brown,48);
// Long floppy ears are tapered, flattened sweeps: their centerlines never point upward.
function ear(sign){const g=new T.Group();g.position.set(sign*.79,2.21,-.01);root.add(g);const anchors=[[0,0,0],[.30,-.03,-.02],[.55,-.20,-.04],[.72,-.43,-.02],[.80,-.68,.01],[.79,-.97,.035]];const c=new T.CatmullRomCurve3(anchors.map(p=>new T.Vector3(sign*p[0],p[1],p[2])));const pos=[],uv=[],ix=[];const rows=40,cols=24;const widths=[.17,.24,.29,.32,.25,0];function lerpWidth(t){const q=t*(widths.length-1),a=Math.min(Math.floor(q),widths.length-2);return T.MathUtils.lerp(widths[a],widths[a+1],q-a)}
for(let j=0;j<=rows;j++){const t=j/rows,p=c.getPoint(t),tan=c.getTangent(t);const n=new T.Vector3(-tan.y,tan.x,0).normalize();const w=.31*Math.pow(Math.sin(Math.PI*t),.5),d=w*.58;for(let k=0;k<=cols;k++){const a=k/cols*Math.PI*2;pos.push(p.x+n.x*Math.cos(a)*w,p.y+n.y*Math.cos(a)*w,p.z+Math.sin(a)*d);uv.push(k/cols,t);if(j<rows&&k<cols){const a1=j*(cols+1)+k,b=a1+cols+1;ix.push(a1,a1+1,b,b,a1+1,b+1)}}}
const geo=new T.BufferGeometry();geo.setAttribute('position',new T.Float32BufferAttribute(pos,3));geo.setAttribute('uv',new T.Float32BufferAttribute(uv,2));geo.setIndex(ix);geo.computeVertexNormals();const normals=geo.getAttribute('normal');for(let j=1;j<rows;j++){const ia=j*(cols+1),ib=ia+cols;const n=new T.Vector3().fromBufferAttribute(normals,ia).add(new T.Vector3().fromBufferAttribute(normals,ib)).normalize();normals.setXYZ(ia,n.x,n.y,n.z);normals.setXYZ(ib,n.x,n.y,n.z)}for(const j of [0,rows]){const n=c.getTangent(j/rows).multiplyScalar(j===0?-1:1).normalize();for(let k=0;k<=cols;k++)normals.setXYZ(j*(cols+1)+k,n.x,n.y,n.z)}normals.needsUpdate=true;mesh(g,white,[0,0,0],[1,1,1],geo);return g}
const ears=[ear(-1),ear(1)];
// The spiral is behind the body, readable at the left edge without looking like a handle.
const tailPoints=[];for(let i=0;i<=72;i++){const t=i/72,a=-.20+t*Math.PI*2.35,r=.31*(1-t)+.045*t;tailPoints.push([-.86+Math.cos(a)*r,.77+Math.sin(a)*r,-.20+.025*t]);}tube(root,tailPoints,.070,white,72);mesh(root,white,tailPoints[tailPoints.length-1],[.070,.070,.070]);
const floor=new T.Mesh(new T.PlaneGeometry(200,200),new T.ShadowMaterial({color:0x6b7893,opacity:.08}));floor.rotation.x=-Math.PI/2;floor.position.y=0;floor.receiveShadow=true;scene.add(floor);
// Handmade fleece detail stays subtle, with no visible grain at app icon size.
const c=document.createElement('canvas');c.width=c.height=128;const ctx=c.getContext('2d'),data=ctx.createImageData(128,128),h=[];let seed=11;for(let i=0;i<128*128;i++){seed=(seed*1664525+1013904223)>>>0;h.push(126+((seed>>>24)-128)*.18)}for(let y=0;y<128;y++)for(let x=0;x<128;x++){const dx=(h[y*128+(x+1)%128]-h[y*128+(x+127)%128])/255,dy=(h[((y+1)%128)*128+x]-h[((y+127)%128)*128+x])/255,q=Math.sqrt(16*dx*dx+16*dy*dy+1);data.data.set([128+127*dx*4/q,128-127*dy*4/q,128+127/q,255],4*(y*128+x))}ctx.putImageData(data,0,0);const tex=new T.CanvasTexture(c);tex.wrapS=tex.wrapT=T.RepeatWrapping;tex.repeat.set(2,2);white.normalMap=tex;white.normalScale.set(.16,.20);white.needsUpdate=true;
for(const ear of ears)head.attach(ear);
window.study={renderer,scene,camera,root,head,eyes,ears,render:()=>renderer.render(scene,camera),setYaw:y=>{root.rotation.y=y;renderer.render(scene,camera)}};renderer.render(scene,camera);
