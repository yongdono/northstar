// TradeView.spec.js created with Cypress
//
// Start writing your Cypress tests below!
// If you're unfamiliar with how Cypress works,
// check out the link below and learn how to write your first test:
// https://on.cypress.io/writing-first-test
/* eslint-disable */

describe('手工期货交易-测试', () => {
    before(() => {
        cy.visit('https://localhost')
        cy.contains('用户名').parent().find('input').type('admin')
        cy.contains('密码').parent().find('input').type('123456')
        cy.contains('登陆').click()
        cy.wait(500)
        cy.contains('新建').click()
        cy.get('.el-dialog').contains('网关类型').parent().find('.el-select').click()
        cy.get('.el-select-dropdown').contains('SIM').click()
        cy.get('.el-dialog').contains('订阅合约').parent().find('.el-select').type('模拟合约')
        cy.get('.el-select-dropdown').contains('模拟合约').click()
        cy.get('.el-dialog').filter(':visible').find('button').last().click()
        cy.visit('https://localhost/#/tdgateway')
        cy.wait(300)
        cy.get('button').contains('新建').click()
        cy.get('.el-dialog').contains('账户ID').parent().find('input').type('testAccount')
        cy.get('.el-dialog').contains('账户类型').parent().find('.el-select').click()
        cy.get('.el-select-dropdown').filter(':visible').contains('SIM').click()
        cy.get('.el-dialog').contains('行情网关').parent().find('.el-select').click()
        cy.get('.el-select-dropdown').filter(':visible').last().contains('SIM').click()
        cy.get('.el-dialog').filter(':visible').find('button').last().click()
        cy.get('.el-table__row').first().contains('连线').click()
        cy.wait(1000)
        cy.get('.el-table__row').first().contains('出入金').click()
        cy.get('.el-dialog').filter(':visible').find('input').type(50000)
        cy.get('.el-dialog').filter(':visible').find('button').contains('出入金').click()
        cy.wait(1000)
        cy.get('.el-dialog').filter(':visible').find('button').first().click()
        cy.visit('https://localhost/#/mktgateway')
        cy.wait(500)
        cy.get('.el-table__row').first().contains('连线').click()
        cy.wait(1000)
        cy.visit('https://localhost/#/manualfttd')
        cy.wait(300)
        cy.get('.ns-trade__account').find('input').click()
        cy.get('.el-select-dropdown').filter(':visible').contains('testAccount').click()
    })
    beforeEach(() => {
        cy.Cookies.preserveOnce('JSESSIONID')
    })

    it('初始金额显示正确', () => {
        cy.get('.ns-trade__account-profile').contains('权益：50,000')
        cy.get('.ns-trade__account-profile').contains('可用：50,000')
    })

    it('选中合约时，K线数据加载正常', () => {
        cy.intercept('GET', '/northstar/data/bar/min?gatewayId=SIM&unifiedSymbol=sim9999@SHFE@FUTURES&refStartTimestamp=*&firstLoad=true').as('getBars')
        cy.get('#contractSelector').type('sim', {force: true})
        cy.get('.el-select-dropdown').contains('模拟合约').click()
        cy.wait('@getBars').should('have.nested.property', 'response.statusCode', 200)
    })

    it('限价开仓，等待成交，可以查询到委托，可用资金减少；然后撤单，挂单消失，可用资金恢复', () => {
        cy.get('#priceType').click()
        cy.get('.el-select-dropdown').contains('限价').parent().click()
        cy.wait(200)
        cy.get('#limitPrice').type(1000)
        cy.get('.ns-trade-button').first().click()
        cy.wait(500)
        cy.get('.ns-trade__account-profile').contains('可用：4')
        cy.contains('委托').click()
        cy.get('.el-table__row').filter(':visible').should('have.length', 1)
        cy.contains('挂单').click()
        cy.get('.el-table__row').filter(':visible').should('have.length', 1)
        cy.get('.el-table__row').filter(':visible').find('button').filter(':visible').click()
        cy.get('.el-popconfirm').find('button').contains('确定').click()
        cy.get('.ns-trade__account-profile').contains('可用：50,000')
        cy.wait(500)
    })
    
    it('市价开仓，立即成交，可以查询到持仓、委托、成交记录', () => {
        cy.get('#priceType').click()
        cy.get('.el-select-dropdown').contains('市价').parent().click()
        cy.get('.ns-trade-button').first().click()
        cy.wait(500)
        cy.contains('持仓').click()
        cy.get('.el-table__row').filter(':visible').should('have.length', 1)
        cy.contains('委托').click()
        cy.get('.el-table__row').filter(':visible').should('have.length', 2)
        cy.contains('成交').click()
        cy.get('.el-table__row').filter(':visible').should('have.length', 1)
    })

    it('排队价平仓，等待成交，可以持仓减少；然后撤单，挂单消失，可用持仓恢复', () => {
        cy.contains('持仓').click()
        cy.get('.el-table__row').filter(':visible').click()
        cy.get('#priceType').click()
        cy.get('.el-select-dropdown').contains('限价').parent().click()
        cy.get('#limitPrice').type(10000)
        cy.get('.ns-trade-button').last().click()
        cy.wait(500)
        cy.contains('持仓').click()
        cy.get('.el-table__row').find('.cell').eq(3).filter(':visible').should('have.text', ' 0 ')
        cy.contains('挂单').click()
        cy.get('.el-table__row').filter(':visible').should('have.length', 1)
        cy.get('.el-table__row').filter(':visible').find('button').filter(':visible').click()
        cy.get('.el-popconfirm').find('button').contains('确定').click()
        cy.contains('持仓').click()
        cy.get('.el-table__row').find('.cell').eq(3).filter(':visible').should('have.text', ' 1 ')
    })

    it('对手价平仓，立即成交，可以查询到持仓、委托、成交记录', () => {
        cy.contains('持仓').click()
        cy.wait(500)
        cy.get('.el-table__row').filter(':visible').click()
        cy.get('.ns-trade-button').last().click()
        cy.wait(500)
        cy.contains('持仓').click()
        cy.get('.el-table__row').filter(':visible').should('have.length', 0)
    })

    after(() => {
        cy.request('DELETE', 'https://localhost/northstar/gateway/connection?gatewayId=testAccount')
        cy.request('DELETE', 'https://localhost/northstar/gateway/connection?gatewayId=SIM')
        cy.request('DELETE', 'https://localhost/northstar/gateway?gatewayId=testAccount')
        cy.request('DELETE', 'https://localhost/northstar/gateway?gatewayId=SIM')
    })
})