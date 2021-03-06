import validator from "../behaviors/validator";
Component({
    externalClasses: ["l-class"],
    behaviors: [validator],
    options: {
        multipleSlots: !0
    },
    relations: {
        "../step/index": {
            type: "child",
            linked() {
                this._initSteps()
            },
            unlinked() {
                this._initSteps()
            }
        }
    },
    properties: {
        direction: {
            type: String,
            value: "row",
            options: ["row", "column"]
        },
        activeIndex: {
            type: Number,
            value: 0
        },
        color: String,
        stepMinHeight: {
            type: String,
            value: "120"
        },
        status: {
            type: String,
            value: "process",
            options: ["process", "error"]
        },
        dot: Boolean,
        reverse: Boolean
    },
    observers: {
        activeIndex: function () {
            this._initSteps()
        }
    },
    data: {},
    methods: {
        _initSteps() {
            wx.createSelectorQuery().in(this).select(".steps-container").boundingClientRect().exec(e => {
                let t = this.getRelationNodes("../step/index");
                this.data.length = t.length, this.data.length > 0 && t.forEach((t, i) => {
                    t.updateDataChange({
                        index: i,
                        ...this.data,
                        stepsWidth: e[0].width
                    })
                })
            })
        }
    }
});