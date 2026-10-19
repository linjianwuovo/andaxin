workspace "安大信现状架构模型" {
    !theme
    !adrs

    properties {
        "generated.by" "system-modeler + c4model（architecture-visualization 插件）"
        "generated.date" "2026-10-19"
        "generated.source" "git show beta:… 现读，未 checkout；前仓在同级目录 lightxin-main"
        "view.state" "current-state only（目标分层见 target-layering.dot）"
        "confidence.policy" "每个元素 description 末尾标 high|medium|low；未标即 high"
    }

    model {

        student = person "学生用户" "手机上的使用者；同一校园账号可能同时在官方 App 登录，两边会互相顶号" {
            tags "Person"
        }

        # 外部系统：全部是校方或第三方厂商的服务端，本仓不含任何自建服务端
        jw = softwareSystem "教务与统一门户" "in.aiit.edu.cn：BASE_MAIN / BASE_AUTH / BASE_CSH / BASE_SCPS，*.do 接口" {
            tags "System", "External", "SchoolBackend"
        }
        pass = softwareSystem "统一身份认证" "aiitpass.fifedu.com：账号口令换 token，RSA 加密口令" {
            tags "System", "External", "SchoolBackend"
        }
        checkin = softwareSystem "查寝系统" "fdygl.aiit.edu.cn：签到任务列表与详情" {
            tags "System", "External", "SchoolBackend"
        }
        credit = softwareSystem "素质学分" "cqc.aiit.edu.cn：学分总览" {
            tags "System", "External", "SchoolBackend"
        }
        labor = softwareSystem "劳动教育" "ldjy.aiit.edu.cn：工时汇总" {
            tags "System", "External", "SchoolBackend"
        }
        leave = softwareSystem "待办事务（请假/销假）" "ywlz.aiit.edu.cn/zhxy-bfc：表单结构由 getFlowSheet.do 动态给出" {
            tags "System", "External", "SchoolBackend"
        }
        repair = softwareSystem "报修" "repair.aiit.edu.cn：单据列表、详情、新建" {
            tags "System", "External", "SchoolBackend"
        }
        sports = softwareSystem "智慧体育" "http://sports.aiit.edu.cn:8082：跑步上传 addExtraCheckNew.do；明文 http 且仅校内可达" {
            tags "System", "External", "SchoolBackend"
        }
        aiclass = softwareSystem "AI 课堂" "sttp.fifedu.com + resc.fifedu.com：课程、课件页图、直录播、小组讨论、投票" {
            tags "System", "External", "ThirdParty"
        }
        homework = softwareSystem "网络作业" "izuoye.fifedu.com：作业详情" {
            tags "System", "External", "ThirdParty", "LowConfidence"
        }
        ecard = softwareSystem "校园卡" "17wanxiao.com（ecardh5 / hub / payadapter / wapnew）：余额、流水、挂失；需先用校方会话换厂商会话" {
            tags "System", "External", "ThirdParty"
        }
        tiles = softwareSystem "高德街道瓦片" "webrd0x 瓦片服务：内置地图底图，坐标网格是 GCJ-02" {
            tags "System", "External", "Basemap"
        }
        releases = softwareSystem "GitHub Releases" "安装包直链与版本检查" {
            tags "System", "External"
        }

        andaxin = softwareSystem "安大信 App" "com.linxin：单 Android 客户端，无自建后端；beta 分支 195 个 Kotlin 文件 / 36845 行" {
            tags "System"

            shell = container "应用壳与导航" "MainActivity + navigation/：NavGraph 567 行、33 个 destination、Routes 常量；SplashOverlay 决定落点" {
                tags "Container"
            }
            ui = container "界面层 feature/*/ui" "38 个 Screen 级 composable（Jetpack Compose + Material3 + Miuix + Kyant 玻璃）；底栏五格定义在 feature/home/ui/HomeScreen.kt:50-56" {
                tags "Container", "Ui"
            }
            state = container "状态与视图模型" "各 feature 的 ViewModel / UiState；HomeDashboard 一个人聚合六个 Repository" {
                tags "Container"
            }
            proto = container "协议层 feature/*/data" "Repository + Retrofit Api + Parse + 上传编码（SportsUploadCodec RSA）；这一层是校方报文的知识所在" {
                tags "Container", "Protocol"
            }
            core = container "平台适配 core/*" "auth 会话与 token 刷新、network、location/InternalGpsFeed、map/LiveTrackMap、notification 三路实况后端、settings 13 个 DataStore、designsystem 17 文件 1535 行" {
                tags "Container", "Platform"
            }
            res = container "文案与资源 res/*" "values/strings.xml 与 values-en 双语，界面文案集中在这里" {
                tags "Container", "Resources"
            }

            storeAuth = container "本机存储" "DataStore：linxin_auth / linxin_login / linxin_saved_credential / linxin_theme / linxin_dev / linxin_messages + 4 个 poll 窗口；文件 filesDir/route_templates.json 存自绘路线" {
                tags "Container", "Datastore"
            }
        }

        # 关系
        student -> ui "用" "Compose 界面；写操作（报修提交、请假提交、销假、挂失、付款）一律要人按"
        shell -> ui "承载 33 个路由"
        ui -> state "观察 UiState，上抛事件"
        state -> proto "调用" "in-process"
        proto -> core "取会话 / 取定位 / 发实况通知"
        proto -> jw "课表、成绩、公告、消息、门户" "HTTPS *.do"
        proto -> pass "登录换 token" "HTTPS + RSA"
        proto -> checkin "查寝"
        proto -> credit "学分"
        proto -> labor "工时"
        proto -> leave "待办事务与请假"
        proto -> repair "报修"
        proto -> sports "跑步上传（9 个键加密、runningRoute 明文）" "HTTP，仅校内"
        proto -> aiclass "AI 课堂"
        proto -> homework "作业详情" "confidence=low：Api 里零 .do 零抓包注释"
        proto -> ecard "校园卡"
        proto -> releases "查版本 / 取安装包"
        ui -> tiles "拉瓦片画轨迹与路线" "GCJ-02 网格；上图前 WGS-84→GCJ-02"
        proto -> storeAuth "读写会话、主题、已读、路线"
        core -> storeAuth "读写"

        # 现状里已知的死角，明确标出来而不是抹平
        deadRunning = container "旧运动实现 feature/running" "24 个文件；NavGraph 仍注册 running/club 与 running/exercise 两条路由，但全仓无 navigate 调用，实际进不去；core/location/InternalGpsFeed.kt 还 import 它的 GeoDistance/TrackPoint" {
            tags "Container", "Deprecated"
        }

        shell -> deadRunning "仍注册两条死路由" "confidence=high（NavGraph:70-71）"
    }

    views {
        systemLandscape "L0-landscape" {
            include *
            autoLayout
        }

        systemContext "L1-context" {
            include student andaxin jw pass sports leave ecard aiclass tiles releases
            autoLayout tiered
        }

        container "L2-containers" {
            include andaxin.* shell ui state proto core res storeAuth deadRunning
            autoLayout
        }

        styles {
            element "Person" {
                shape person
                background #08427b
                color #ffffff
            }
            element "External" {
                background #999999
                color #ffffff
            }
            element "ThirdParty" {
                background #b8860b
            }
            element "SchoolBackend" {
                background #6b8e23
            }
            element "Ui" {
                background #1f6feb
            }
            element "Protocol" {
                background #8957e5
            }
            element "Datastore" {
                shape cylinder
                background #4b5563
            }
            element "Deprecated" {
                background #b91c1c
                color #ffffff
                border #7f1d1d
            }
        }
    }
}
