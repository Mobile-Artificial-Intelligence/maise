import { Asset } from "expo-asset";

export interface KokoroVoice {
  id: string;
  name: string;
  nationality: string;
  gender: string;
}

export const KokoroVoiceMap: Record<string, KokoroVoice> = {
  af_alloy: {
    id: "af_alloy",
    name: "Alloy",
    nationality: "American",
    gender: "Female"
  },
  af_aoede: {
    id: "af_aoede",
    name: "Aoede",
    nationality: "American",
    gender: "Female"
  },
  af_bella: {
    id: "af_bella",
    name: "Bella",
    nationality: "American",
    gender: "Female"
  },
  af_heart: {
    id: "af_heart",
    name: "Heart",
    nationality: "American",
    gender: "Female"
  },
  af_jessica: {
    id: "af_jessica",
    name: "Jessica",
    nationality: "American",
    gender: "Female"
  },
  af_kore: {
    id: "af_kore",
    name: "Kore",
    nationality: "American",
    gender: "Female"
  },
  af_nicole: {
    id: "af_nicole",
    name: "Nicole",
    nationality: "American",
    gender: "Female"
  },
  af_nova: {
    id: "af_nova",
    name: "Nova",
    nationality: "American",
    gender: "Female"
  },
  af_river: {
    id: "af_river",
    name: "River",
    nationality: "American",
    gender: "Female"
  },
  af_sarah: {
    id: "af_sarah",
    name: "Sarah",
    nationality: "American",
    gender: "Female"
  },
  af_sky: {
    id: "af_sky",
    name: "Sky",
    nationality: "American",
    gender: "Female"
  },
  am_adam: {
    id: "am_adam",
    name: "Adam",
    nationality: "American",
    gender: "Male"
  },
  am_echo: {
    id: "am_echo",
    name: "Echo",
    nationality: "American",
    gender: "Male"
  },
  am_eric: {
    id: "am_eric",
    name: "Eric",
    nationality: "American",
    gender: "Male"
  },
  am_fenrir: {
    id: "am_fenrir",
    name: "Fenrir",
    nationality: "American",
    gender: "Male"
  },
  am_liam: {
    id: "am_liam",
    name: "Liam",
    nationality: "American",
    gender: "Male"
  },
  am_michael: {
    id: "am_michael",
    name: "Michael",
    nationality: "American",
    gender: "Male"
  },
  am_onyx: {
    id: "am_onyx",
    name: "Onyx",
    nationality: "American",
    gender: "Male"
  },
  am_puck: {
    id: "am_puck",
    name: "Puck",
    nationality: "American",
    gender: "Male"
  },
  am_santa: {
    id: "am_santa",
    name: "Santa",
    nationality: "American",
    gender: "Male"
  },
  bf_alice: {
    id: "bf_alice",
    name: "Alice",
    nationality: "British",
    gender: "Female"
  },
  bf_emma: {
    id: "bf_emma",
    name: "Emma",
    nationality: "British",
    gender: "Female"
  },
  bf_isabella: {
    id: "bf_isabella",
    name: "Isabella",
    nationality: "British",
    gender: "Female"
  },
  bf_lily: {
    id: "bf_lily",
    name: "Lily",
    nationality: "British",
    gender: "Female"
  },
  bm_daniel: {
    id: "bm_daniel",
    name: "Daniel",
    nationality: "British",
    gender: "Male"
  },
  bm_fable: {
    id: "bm_fable",
    name: "Fable",
    nationality: "British",
    gender: "Male"
  },
  bm_george: {
    id: "bm_george",
    name: "George",
    nationality: "British",
    gender: "Male"
  },
  bm_lewis: {
    id: "bm_lewis",
    name: "Lewis",
    nationality: "British",
    gender: "Male"
  },
  ef_dora: {
    id: "ef_dora",
    name: "Dora",
    nationality: "European",
    gender: "Female"
  },
  em_alex: {
    id: "em_alex",
    name: "Alex",
    nationality: "European",
    gender: "Male"
  },
  em_santa: {
    id: "em_santa",
    name: "Santa",
    nationality: "European",
    gender: "Male"
  },
  ff_siwis: {
    id: "ff_siwis",
    name: "Siwis",
    nationality: "French",
    gender: "Female"
  },
  hf_alpha: {
    id: "hf_alpha",
    name: "Alpha",
    nationality: "Hellenic",
    gender: "Female"
  },
  hf_beta: {
    id: "hf_beta",
    name: "Beta",
    nationality: "Hellenic",
    gender: "Female"
  },
  hm_omega: {
    id: "hm_omega",
    name: "Omega",
    nationality: "Hellenic",
    gender: "Male"
  },
  hm_psi: {
    id: "hm_psi",
    name: "Psi",
    nationality: "Hellenic",
    gender: "Male"
  },
  if_sara: {
    id: "if_sara",
    name: "Sara",
    nationality: "Italian",
    gender: "Female"
  },
  im_nicola: {
    id: "im_nicola",
    name: "Nicola",
    nationality: "Italian",
    gender: "Male"
  },
  jf_alpha: {
    id: "jf_alpha",
    name: "Alpha",
    nationality: "Japanese",
    gender: "Female"
  },
  jf_gongitsune: {
    id: "jf_gongitsune",
    name: "Gongitsune",
    nationality: "Japanese",
    gender: "Female"
  },
  jf_nezumi: {
    id: "jf_nezumi",
    name: "Nezumi",
    nationality: "Japanese",
    gender: "Female"
  },
  jf_tebukuro: {
    id: "jf_tebukuro",
    name: "Tebukuro",
    nationality: "Japanese",
    gender: "Female"
  },
  jm_kumo: {
    id: "jm_kumo",
    name: "Kumo",
    nationality: "Japanese",
    gender: "Male"
  },
  pf_dora: {
    id: "pf_dora",
    name: "Dora",
    nationality: "Portuguese",
    gender: "Female"
  },
  pm_alex: {
    id: "pm_alex",
    name: "Alex",
    nationality: "Portuguese",
    gender: "Male"
  },
  pm_santa: {
    id: "pm_santa",
    name: "Santa",
    nationality: "Portuguese",
    gender: "Male"
  },
  zf_xiaobei: {
    id: "zf_xiaobei",
    name: "Xiaobei",
    nationality: "Chinese",
    gender: "Female"
  },
  zf_xiaoni: {
    id: "zf_xiaoni",
    name: "Xiaoni",
    nationality: "Chinese",
    gender: "Female"
  },
  zf_xiaoxiao: {
    id: "zf_xiaoxiao",
    name: "Xiaoxiao",
    nationality: "Chinese",
    gender: "Female"
  },
  zf_xiaoyi: {
    id: "zf_xiaoyi",
    name: "Xiaoyi",
    nationality: "Chinese",
    gender: "Female"
  },
  zm_yunjian: {
    id: "zm_yunjian",
    name: "Yunjian",
    nationality: "Chinese",
    gender: "Male"
  },
  zm_yunxi: {
    id: "zm_yunxi",
    name: "Yunxi",
    nationality: "Chinese",
    gender: "Male"
  },
  zm_yunxia: {
    id: "zm_yunxia",
    name: "Yunxia",
    nationality: "Chinese",
    gender: "Male"
  },
  zm_yunyang: {
    id: "zm_yunyang",
    name: "Yunyang",
    nationality: "Chinese",
    gender: "Male"
  }
} as const;

export const KokoroVoiceIds = Object.keys(KokoroVoiceMap);

export const KokoroVoices = Object.values(KokoroVoiceMap);

const KokoroVoiceAssetMap: Record<string, number> = {
  af_alloy: require("./af_alloy.bin"),
  af_aoede: require("./af_aoede.bin"),
  af_bella: require("./af_bella.bin"),
  af_heart: require("./af_heart.bin"),
  af_jessica: require("./af_jessica.bin"),
  af_kore: require("./af_kore.bin"),
  af_nicole: require("./af_nicole.bin"),
  af_nova: require("./af_nova.bin"),
  af_river: require("./af_river.bin"),
  af_sarah: require("./af_sarah.bin"),
  af_sky: require("./af_sky.bin"),
  am_adam: require("./am_adam.bin"),
  am_echo: require("./am_echo.bin"),
  am_eric: require("./am_eric.bin"),
  am_fenrir: require("./am_fenrir.bin"),
  am_liam: require("./am_liam.bin"),
  am_michael: require("./am_michael.bin"),
  am_onyx: require("./am_onyx.bin"),
  am_puck: require("./am_puck.bin"),
  am_santa: require("./am_santa.bin"),
  bf_alice: require("./bf_alice.bin"),
  bf_emma: require("./bf_emma.bin"),
  bf_isabella: require("./bf_isabella.bin"),
  bf_lily: require("./bf_lily.bin"),
  bm_daniel: require("./bm_daniel.bin"),
  bm_fable: require("./bm_fable.bin"),
  bm_george: require("./bm_george.bin"),
  bm_lewis: require("./bm_lewis.bin"),
  ef_dora: require("./ef_dora.bin"),
  em_alex: require("./em_alex.bin"),
  em_santa: require("./em_santa.bin"),
  ff_siwis: require("./ff_siwis.bin"),
  hf_alpha: require("./hf_alpha.bin"),
  hf_beta: require("./hf_beta.bin"),
  hm_omega: require("./hm_omega.bin"),
  hm_psi: require("./hm_psi.bin"),
  if_sara: require("./if_sara.bin"),
  im_nicola: require("./im_nicola.bin"),
  jf_alpha: require("./jf_alpha.bin"),
  jf_gongitsune: require("./jf_gongitsune.bin"),
  jf_nezumi: require("./jf_nezumi.bin"),
  jf_tebukuro: require("./jf_tebukuro.bin"),
  jm_kumo: require("./jm_kumo.bin"),
  pf_dora: require("./pf_dora.bin"),
  pm_alex: require("./pm_alex.bin"),
  pm_santa: require("./pm_santa.bin"),
  zf_xiaobei: require("./zf_xiaobei.bin"),
  zf_xiaoni: require("./zf_xiaoni.bin"),
  zf_xiaoxiao: require("./zf_xiaoxiao.bin"),
  zf_xiaoyi: require("./zf_xiaoyi.bin"),
  zm_yunjian: require("./zm_yunjian.bin"),
  zm_yunxi: require("./zm_yunxi.bin"),
  zm_yunxia: require("./zm_yunxia.bin"),
  zm_yunyang: require("./zm_yunyang.bin")
} as const;

export async function load_voice_data(voice: KokoroVoice): Promise<Float32Array> {
  const asset = Asset.fromModule(KokoroVoiceAssetMap[voice.id]);
  if (!asset.downloaded) {
    await asset.downloadAsync();
  }
  const uri = asset.localUri ?? asset.uri;
  const res = await fetch(uri);
  const buf = await res.arrayBuffer();
  return new Float32Array(buf);
}