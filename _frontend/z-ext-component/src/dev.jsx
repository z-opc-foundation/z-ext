import React from 'react'
import ReactDOM from 'react-dom/client'
import ExtensionPointTag from './ExtensionPointTag'

const root = ReactDOM.createRoot(document.getElementById('root'))
root.render(
    <div style={{padding: 40, display: 'flex', flexDirection: 'column', gap: 12}}>
        <ExtensionPointTag type="POINT" name="OrderPay" bizType="order"/>
        <ExtensionPointTag type="IMPL" name="AlipayPay" bizType="order"/>
    </div>
)
