import Vue from 'vue';
import Router from 'vue-router';
import store from '../store'
import util from '../util'

import heartBeat from '../template/common/heartBeat'
import tabPage from '../template/common/tabPage'
import signUp from '../template/page/signUp'
import signIn from '../template/page/signIn'
import game from '../template/page/game'
import start from '../template/page/start'
import history from '../template/page/history'
import mine from '../template/page/mine'
import chat from '../template/page/chat'
import notFound from '../template/page/notFound'

Vue.use(Router);
const router = new Router({
    routes: [
        {
            path: '/',
            component: heartBeat,
            meta: {title: 'index'},
            children: [
                {
                    path: '',
                    redirect: '/tab'
                },
                {
                    path: '/sign-up',
                    component: signUp,
                    meta: {
                        title: '注册',
                        ignoreLogin: true
                    }
                },
                {
                    path: '/sign-in',
                    component: signIn,
                    meta: {
                        title: '登陆',
                        ignoreLogin: true
                    }
                },
                {
                    path: '/game',
                    component: game,
                    meta: {title: '对局'}
                }, {
                    path: '/history',
                    component: history,
                    meta: {title: '历史'}
                },
                {
                    path: '/tab',
                    component: tabPage,
                    children: [
                        {
                            path: '',
                            redirect: 'start'
                        },
                        {
                            path: 'start',
                            component: start,
                            meta: {
                                showNotice: false,
                                icon: 'wap-home-o',
                                tabName: '首页'
                            }
                        },
                        {
                            path: 'chat',
                            component: chat,
                            meta: {
                                showNotice: true,
                                icon: 'chat-o',
                                tabName: '聊天'
                            }
                        },
                        {
                            path: 'mine',
                            component: mine,
                            meta: {
                                showNotice: true,
                                icon: 'manager-o',
                                tabName: '我'
                            }
                        },
                    ]
                },
            ]
        }, {
            name: 'notFound',
            path: '/404',
            component: notFound,
            meta: {
                title: '404',
                ignoreLogin: true
            }
        },
        {
            path: '*',
            redirect: '/404',
            meta: {
                ignoreLogin: true
            }
        }
    ]
});

router.beforeEach((to, from, next) => {
    let ignoreLogin = to.meta.ignoreLogin;
    ignoreLogin=true;
    if (!ignoreLogin) {
        let userToken = util.getUserToken();
        if (!userToken) {
            next('/sign-in');
            return
        }
    }
    let toTitle = to.meta.title;
    document.title = `五子棋`;
    if (toTitle) {
        document.title = `${toTitle} | 五子棋`;
    }
    store.state.activeNavItemIdx = to.meta.navItemIdx;
    next();
});

export default router;
