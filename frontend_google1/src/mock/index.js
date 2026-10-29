// Helper to get or set localStorage data
const getStorageItem = (key, defaultValue) => {
  const item = localStorage.getItem(key);
  if (item) {
    try {
      return JSON.parse(item);
    } catch (e) {
      return defaultValue;
    }
  }
  localStorage.setItem(key, JSON.stringify(defaultValue));
  return defaultValue;
};

const setStorageItem = (key, value) => {
  localStorage.setItem(key, JSON.stringify(value));
};

const INITIAL_PRODUCTS = [
  {
    id: "1",
    name: "麦乐迪手剥手松子 (原味)",
    price: 38.8,
    marketPrice: 48.0,
    description: "严选长白山野生红松，物理慢烘，粒粒饱满易剥，浓郁松香，健康零添加。",
    image: "https://images.unsplash.com/photo-1595855759920-86582396756a?auto=format&fit=crop&q=80&w=400",
    category: "坚果炒货",
    inventory: 450,
    sales: 1284,
    tags: ["低糖健康", "野生严选", "会员特惠"],
    rating: 4.9,
    specs: ["经典原味 180g", "盐焗风味 180g", "超值分享装 360g"],
    skuList: [
      { sku_id: 11, spec_name: "经典原味 180g", price: 38.8, stock: 150 },
      { sku_id: 12, spec_name: "盐焗风味 180g", price: 42.0, stock: 120 },
      { sku_id: 13, spec_name: "超值分享装 360g", price: 75.0, stock: 180 }
    ],
    details: {
      origin: "吉林省白山市",
      shelfLife: "240天",
      weight: "180g",
      ingredients: "野生红松子、食盐（仅盐焗风味）"
    }
  },
  {
    id: "2",
    name: "炭烤香辣牛肉干",
    price: 45.0,
    marketPrice: 58.0,
    description: "选用优质黄牛后腿肉，秘制香料慢火炭烤，纤维清晰，柔韧有劲，香辣入味。",
    image: "https://images.unsplash.com/photo-1626082927389-6cd097cdc6ec?auto=format&fit=crop&q=80&w=400",
    category: "肉干卤味",
    inventory: 310,
    sales: 932,
    tags: ["高蛋白", "地道川味", "爆款推荐"],
    rating: 4.8,
    specs: ["劲辣风味 150g", "经典五香 150g"],
    skuList: [
      { sku_id: 21, spec_name: "劲辣风味 150g", price: 45.0, stock: 160 },
      { sku_id: 22, spec_name: "经典五香 150g", price: 45.0, stock: 150 }
    ],
    details: {
      origin: "四川省成都市",
      shelfLife: "180天",
      weight: "150g",
      ingredients: "黄牛肉、植物油、辣椒、花椒、白砂糖、食用盐、香辛料"
    }
  },
  {
    id: "3",
    name: "流心奶黄绿豆糕",
    price: 26.8,
    marketPrice: 32.0,
    description: "精选去皮绿豆沙，包裹金黄咸蛋黄流心，入口即化，甜而不腻，清凉解暑。",
    image: "https://images.unsplash.com/photo-1569864358642-9d1684040f43?auto=format&fit=crop&q=80&w=400",
    category: "糕点点心",
    inventory: 180,
    sales: 621,
    tags: ["控糖配方", "纯手作", "精致下午茶"],
    rating: 4.7,
    specs: ["经典流心 6枚装", "海盐芝士 6枚装"],
    skuList: [
      { sku_id: 31, spec_name: "经典流心 6枚装", price: 26.8, stock: 90 },
      { sku_id: 32, spec_name: "海盐芝士 6枚装", price: 28.5, stock: 90 }
    ],
    details: {
      origin: "浙江省杭州市",
      shelfLife: "60天",
      weight: "240g (40g * 6)",
      ingredients: "绿豆、海藻糖、咸鸭蛋黄、黄油、稀奶油、小麦粉"
    }
  },
  {
    id: "4",
    name: "无核厚切大芒果干",
    price: 22.8,
    marketPrice: 28.0,
    description: "精选优质攀枝花凯特芒果，厚切饱满，低温慢速烘干，保留自然果香与酸甜Q弹。",
    image: "https://images.unsplash.com/photo-1596003903067-bf5762a5479e?auto=format&fit=crop&q=80&w=400",
    category: "果干蜜饯",
    inventory: 520,
    sales: 2481,
    tags: ["0防腐剂", "维生素C", "果肉厚实"],
    rating: 4.9,
    specs: ["无酸厚切 200g", "酸甜多汁 200g"],
    skuList: [
      { sku_id: 41, spec_name: "无酸厚切 200g", price: 22.8, stock: 260 },
      { sku_id: 42, spec_name: "酸甜多汁 200g", price: 22.8, stock: 260 }
    ],
    details: {
      origin: "四川省攀枝花市",
      shelfLife: "12个月",
      weight: "200g",
      ingredients: "鲜芒果、白砂糖、柠檬酸"
    }
  },
  {
    id: "5",
    name: "酥脆海盐牛油果薯片",
    price: 15.8,
    marketPrice: 19.8,
    description: "创新牛油果泥融入优质薯粉，非油炸低温慢烘，海盐提鲜，轻负担超酥脆。",
    image: "https://images.unsplash.com/photo-1566478989037-eec170784d0b?auto=format&fit=crop&q=80&w=400",
    category: "膨化薯片",
    inventory: 640,
    sales: 1753,
    tags: ["非油炸", "低饱和脂肪", "网红新品"],
    rating: 4.6,
    specs: ["经典海盐味 75g", "芥末沙拉味 75g", "家庭分享桶 225g"],
    skuList: [
      { sku_id: 51, spec_name: "经典海盐味 75g", price: 15.8, stock: 300 },
      { sku_id: 52, spec_name: "芥末沙拉味 75g", price: 15.8, stock: 240 },
      { sku_id: 53, spec_name: "家庭分享桶 225g", price: 42.0, stock: 100 }
    ],
    details: {
      origin: "上海市",
      shelfLife: "270天",
      weight: "75g",
      ingredients: "马铃薯淀粉、新鲜牛油果泥、植物油、海盐、洋葱粉、酵母提取物"
    }
  },
  {
    id: "6",
    name: "精选临安炭焙核桃仁",
    price: 32.8,
    marketPrice: 39.8,
    description: "精选浙江临安野生山核桃，传统手艺木炭烘焙，精心去壳留仁，金黄酥脆，回甘无穷。",
    image: "https://images.unsplash.com/photo-1607623814075-e51df1bdc82f?auto=format&fit=crop&q=80&w=400",
    category: "坚果炒货",
    inventory: 280,
    sales: 843,
    tags: ["野生山核桃", "无壳即食", "益智健脑"],
    rating: 4.8,
    specs: ["经典草本味 150g", "椒盐海苔味 150g"],
    skuList: [
      { sku_id: 61, spec_name: "经典草本味 150g", price: 32.8, stock: 140 },
      { sku_id: 62, spec_name: "椒盐海苔味 150g", price: 34.5, stock: 140 }
    ],
    details: {
      origin: "浙江省杭州市临安区",
      shelfLife: "180天",
      weight: "150g",
      ingredients: "山核桃仁、白砂糖、食用盐、草本香料"
    }
  }
];

const INITIAL_USER = {
  id: "U99823",
  username: "melody_fan",
  nickname: "快乐星猫",
  avatar: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&q=80&w=150",
  rank: "金牌会员",
  points: 1280,
  balance: 350.0,
  phone: "188****7788",
  email: "l3146553044@gmail.com",
  joinDate: "2024-05-12"
};

const INITIAL_COUPONS = [
  {
    id: "CP01",
    title: "新客专享全场券",
    discount: 5.0,
    minSpend: 20.0,
    expiryDate: "2026-08-31",
    status: "可用",
    description: "限麦乐迪新客使用，全品类通用，满20元立减5元"
  },
  {
    id: "CP02",
    title: "年中大促坚果神券",
    discount: 15.0,
    minSpend: 69.0,
    expiryDate: "2026-07-31",
    status: "可用",
    description: "限坚果炒货分类商品，满69元立减15元"
  },
  {
    id: "CP03",
    title: "吃货节无门槛红包",
    discount: 10.0,
    minSpend: 10.0,
    expiryDate: "2026-07-20",
    status: "可用",
    description: "零食商场无门槛立减，全场通用"
  }
];

const INITIAL_ADDRESSES = [
  {
    id: "AD01",
    name: "李小璐",
    phone: "138****4567",
    province: "上海市",
    city: "上海市",
    district: "徐汇区",
    detail: "虹桥路 3 号港汇恒隆广场 A 座 12 楼",
    tag: "公司",
    isDefault: true
  },
  {
    id: "AD02",
    name: "快乐星猫",
    phone: "188****7788",
    province: "浙江省",
    city: "杭州市",
    district: "西湖区",
    detail: "文三路 477 号华星科技大厦 3 楼 301 室",
    tag: "家",
    isDefault: false
  }
];

const INITIAL_ORDERS = [
  {
    id: "ORD-20260716-8891",
    date: "2026-07-16 14:32:45",
    status: "PENDING_SHIP",
    items: [
      {
        product: INITIAL_PRODUCTS[0],
        quantity: 2,
        spec: "经典原味 180g",
        price: 38.8
      }
    ],
    totalPrice: 77.6,
    consignee: {
      name: "李小璐",
      phone: "138****4567",
      address: "上海市 徐汇区 虹桥路 3 号港汇恒隆广场 A 座 12 楼"
    },
    shippingFee: 0
  }
];

const INITIAL_CAMPAIGNS = [
  {
    id: "C01",
    title: "年中囤货节 - 全场松子买二赠一",
    type: "满减",
    status: "进行中",
    startDate: "2026-07-01",
    endDate: "2026-07-31",
    banner: "https://images.unsplash.com/photo-1511113647221-ed311a6a5885?auto=format&fit=crop&q=80&w=800"
  },
  {
    id: "C02",
    title: "低糖健康主义 - 烘焙点心限时8折",
    type: "折扣",
    status: "进行中",
    startDate: "2026-07-10",
    endDate: "2026-07-20",
    banner: "https://images.unsplash.com/photo-1559622485-4ffdfc712c44?auto=format&fit=crop&q=80&w=800"
  }
];

const INITIAL_LOGS = [
  { id: "L01", time: "2026-07-16 11:02:14", event: "管理员 admin 更新了商品[炭烤香辣牛肉干]的库存", operator: "系统" },
  { id: "L02", time: "2026-07-16 10:45:00", event: "用户 U99823 [快乐星猫] 领取了优惠券 [吃货节无门槛红包]", operator: "用户" },
  { id: "L03", time: "2026-07-16 09:12:35", event: "订单 ORD-20260716-8891 支付成功，等待发货", operator: "系统" }
];

// F-A-026: 分类树（F-A-026 multi-level category tree）
// parentId 扁平结构，渲染时组装 children
const INITIAL_CATEGORIES = [
  { id: "C1", name: "坚果炒货", parentId: null },
  { id: "C2", name: "肉干卤味", parentId: null },
  { id: "C3", name: "糕点点心", parentId: null },
  { id: "C4", name: "果干蜜饯", parentId: null },
  { id: "C5", name: "膨化薯片", parentId: null },
  { id: "C6", name: "其他", parentId: null },
];

// F-A-060: 管理员账号（默认包含硬编码的超管，演示可增删）
// 注：密码仅前端 mock，真实场景下不应明文存储
const INITIAL_ADMINS = [
  { id: "A1", username: "admin", email: "admin@melody.com", role: "超级管理员", password: "admin123", createdAt: "2026-01-15 10:00:00" },
];

// Load from LocalStorage if exists
export const db = {
  getProducts: () => getStorageItem("melody_products", INITIAL_PRODUCTS),
  setProducts: (data) => setStorageItem("melody_products", data),

  getUser: () => getStorageItem("melody_user", INITIAL_USER),
  setUser: (data) => setStorageItem("melody_user", data),

  getCoupons: () => getStorageItem("melody_coupons", INITIAL_COUPONS),
  setCoupons: (data) => setStorageItem("melody_coupons", data),

  getAddresses: () => getStorageItem("melody_addresses", INITIAL_ADDRESSES),
  setAddresses: (data) => setStorageItem("melody_addresses", data),

  getOrders: () => getStorageItem("melody_orders", INITIAL_ORDERS),
  setOrders: (data) => setStorageItem("melody_orders", data),

  getCampaigns: () => getStorageItem("melody_campaigns", INITIAL_CAMPAIGNS),
  setCampaigns: (data) => setStorageItem("melody_campaigns", data),

  getLogs: () => getStorageItem("melody_logs", INITIAL_LOGS),
  setLogs: (data) => setStorageItem("melody_logs", data),

  getCategories: () => getStorageItem("melody_categories", INITIAL_CATEGORIES),
  setCategories: (data) => setStorageItem("melody_categories", data),

  getAdmins: () => getStorageItem("melody_admins", INITIAL_ADMINS),
  setAdmins: (data) => setStorageItem("melody_admins", data),
};
