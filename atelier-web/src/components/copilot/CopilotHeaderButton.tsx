import { Button } from 'antd';
import { RobotOutlined } from '@ant-design/icons';
import { useCopilot } from './CopilotContext';

export default function CopilotHeaderButton() {
  const { open, setOpen } = useCopilot();

  return (
    <Button
      type={open ? 'primary' : 'default'}
      icon={<RobotOutlined />}
      onClick={() => setOpen(!open)}
    >
      Copilot
    </Button>
  );
}
